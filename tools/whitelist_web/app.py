#!/usr/bin/env python3
import base64
import hashlib
import hmac
import html
import json
import os
import re
import shutil
import socket
import struct
import subprocess
import threading
import time
from datetime import datetime
from http import HTTPStatus
from http.cookies import SimpleCookie
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlencode, urlparse

try:
    import psycopg2
    import psycopg2.extras
except ImportError:
    psycopg2 = None


USERNAME_RE = re.compile(r"^[A-Za-z0-9_]{3,16}$")
UUID_RE = re.compile(r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
RATE_LIMIT_WINDOW_SECONDS = 60
RATE_LIMIT_MAX_ATTEMPTS = 10
SESSION_SECONDS = 7 * 24 * 60 * 60
QUERY_LIMIT = 50
MAX_RADIUS = 200
ATTEMPTS = {}
WHITELIST_LOCK = threading.Lock()


def env(name, default=None, required=False):
    value = os.environ.get(name, default)
    if required and not value:
        raise RuntimeError(f"Missing required environment variable: {name}")
    return value


def env_bool(name, default=False):
    value = os.environ.get(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


HOST = env("WHITELIST_WEB_HOST", "0.0.0.0")
PORT = int(env("WHITELIST_WEB_PORT", "80"))
RCON_HOST = env("XICEMC_RCON_HOST", "127.0.0.1")
RCON_PORT = int(env("XICEMC_RCON_PORT", "25575"))
RCON_PASSWORD = env("XICEMC_RCON_PASSWORD", required=True)
RUNTIME_DIR = env("XICEMC_RUNTIME_DIR", "/opt/xicemc/runtime")
BACKUP_DIR = env("XICEMC_BACKUP_DIR", "/opt/xicemc/backups")
WHITELIST_PATH = env("XICEMC_WHITELIST_PATH", os.path.join(RUNTIME_DIR, "whitelist.json"))
VERIFY_CODES_PATH = env("XICEMC_VERIFY_CODES_PATH", os.path.join(RUNTIME_DIR, "plugins", "XiceTextArranger", "verification-codes.tsv"))
SESSION_SECRET = env("WHITELIST_WEB_SESSION_SECRET", RCON_PASSWORD, required=True)
AUDIT_DB_HOST = env("XICE_AUDIT_DB_HOST", "127.0.0.1")
AUDIT_DB_PORT = int(env("XICE_AUDIT_DB_PORT", "5432"))
AUDIT_DB_NAME = env("XICE_AUDIT_DB_NAME", "xicemc_audit")
AUDIT_DB_USER = env("XICE_AUDIT_DB_USER", "xicemc_audit")
AUDIT_DB_PASSWORD = env("XICE_AUDIT_DB_PASSWORD", "")
AUDIT_RETENTION_DAYS = int(env("XICE_AUDIT_RETENTION_DAYS", "3"))
SERVER_SERVICE_NAME = env("XICEMC_SERVICE_NAME", "xicemc.service")
SERVER_LOG_PATH = env("XICEMC_SERVER_LOG_PATH", os.path.join(RUNTIME_DIR, "logs", "latest.log"))


class RconClient:
    AUTH = 3
    COMMAND = 2

    def __init__(self, host, port, password, timeout=8):
        self.host = host
        self.port = port
        self.password = password
        self.timeout = timeout
        self.request_id = 100

    def _packet(self, packet_type, payload):
        body = struct.pack("<ii", self.request_id, packet_type)
        body += payload.encode("utf-8")
        body += b"\x00\x00"
        return struct.pack("<i", len(body)) + body

    def _read_packet(self, sock):
        raw_length = self._read_exact(sock, 4)
        length = struct.unpack("<i", raw_length)[0]
        data = self._read_exact(sock, length)
        request_id, packet_type = struct.unpack("<ii", data[:8])
        payload = data[8:-2].decode("utf-8", "replace")
        return request_id, packet_type, payload

    @staticmethod
    def _read_exact(sock, size):
        chunks = []
        remaining = size
        while remaining:
            chunk = sock.recv(remaining)
            if not chunk:
                raise ConnectionError("RCON connection closed unexpectedly")
            chunks.append(chunk)
            remaining -= len(chunk)
        return b"".join(chunks)

    def run(self, command):
        with socket.create_connection((self.host, self.port), timeout=self.timeout) as sock:
            sock.settimeout(self.timeout)
            sock.sendall(self._packet(self.AUTH, self.password))
            request_id, _, _ = self._read_packet(sock)
            if request_id == -1:
                raise PermissionError("RCON authentication failed")

            self.request_id += 1
            sock.sendall(self._packet(self.COMMAND, command))
            _, _, payload = self._read_packet(sock)
            return payload.strip()


def rate_limited(ip):
    now = time.time()
    window_start = now - RATE_LIMIT_WINDOW_SECONDS
    attempts = [ts for ts in ATTEMPTS.get(ip, []) if ts >= window_start]
    attempts.append(now)
    ATTEMPTS[ip] = attempts
    return len(attempts) > RATE_LIMIT_MAX_ATTEMPTS


def read_whitelist_entries():
    try:
        with open(WHITELIST_PATH, "r", encoding="utf-8") as file:
            entries = json.load(file)
    except FileNotFoundError:
        return []
    if not isinstance(entries, list):
        return []
    return [entry for entry in entries if isinstance(entry, dict)]


def write_whitelist_entries(entries):
    os.makedirs(os.path.dirname(WHITELIST_PATH), exist_ok=True)
    temp_path = WHITELIST_PATH + ".tmp"
    with open(temp_path, "w", encoding="utf-8") as file:
        json.dump(entries, file, ensure_ascii=False, indent=2)
        file.write("\n")
    os.replace(temp_path, WHITELIST_PATH)


def add_whitelist_entry(entry):
    normalized_uuid = canonical_uuid(entry["uuid"])
    normalized_name = entry["name"]
    new_entry = {"uuid": normalized_uuid, "name": normalized_name}
    with WHITELIST_LOCK:
        entries = read_whitelist_entries()
        replaced = False
        for index, current in enumerate(entries):
            current_uuid = canonical_uuid(current.get("uuid", ""))
            current_name = str(current.get("name", "")).lower()
            if current_uuid == normalized_uuid or current_name == normalized_name.lower():
                entries[index] = new_entry
                replaced = True
                break
        if not replaced:
            entries.append(new_entry)
        write_whitelist_entries(entries)
    return not replaced


def reload_whitelist():
    return RconClient(RCON_HOST, RCON_PORT, RCON_PASSWORD).run("whitelist reload")


def read_whitelist():
    entries = read_whitelist_entries()
    if not entries:
        return {}
    return {entry.get("name", "").lower(): entry for entry in entries if entry.get("name") and entry.get("uuid")}


def whitelist_entry(username):
    return read_whitelist().get(username.lower())


def whitelist_entry_by_uuid(player_uuid):
    normalized_uuid = canonical_uuid(player_uuid)
    for entry in read_whitelist().values():
        if canonical_uuid(entry.get("uuid", "")) == normalized_uuid:
            return entry
    return None


def canonical_uuid(value):
    compact = str(value or "").replace("-", "").lower()
    if len(compact) != 32:
        return str(value or "")
    return f"{compact[0:8]}-{compact[8:12]}-{compact[12:16]}-{compact[16:20]}-{compact[20:32]}"


def b64url(data):
    return base64.urlsafe_b64encode(data).decode("ascii").rstrip("=")


def b64url_decode(value):
    padding = "=" * (-len(value) % 4)
    return base64.urlsafe_b64decode(value + padding)


def sign(value):
    return hmac.new(SESSION_SECRET.encode("utf-8"), value.encode("utf-8"), hashlib.sha256).hexdigest()


def make_session(entry):
    payload = {
        "uuid": canonical_uuid(entry["uuid"]),
        "name": entry["name"],
        "exp": int(time.time()) + SESSION_SECONDS,
    }
    body = b64url(json.dumps(payload, separators=(",", ":")).encode("utf-8"))
    return f"{body}.{sign(body)}"


def parse_session(cookie_header):
    if not cookie_header:
        return None
    cookie = SimpleCookie()
    cookie.load(cookie_header)
    morsel = cookie.get("xicemc_session")
    if not morsel or "." not in morsel.value:
        return None
    body, signature = morsel.value.rsplit(".", 1)
    if not hmac.compare_digest(sign(body), signature):
        return None
    try:
        payload = json.loads(b64url_decode(body).decode("utf-8"))
    except Exception:
        return None
    if int(payload.get("exp", 0)) < int(time.time()):
        return None
    entry = whitelist_entry_by_uuid(payload.get("uuid", "")) or whitelist_entry(payload.get("name", ""))
    if not entry or canonical_uuid(entry.get("uuid")) != canonical_uuid(payload.get("uuid")):
        return None
    return {"uuid": canonical_uuid(entry["uuid"]), "name": entry["name"]}


def db_connect():
    if psycopg2 is None:
        raise RuntimeError("python3 psycopg2 is not installed")
    if not AUDIT_DB_PASSWORD:
        raise RuntimeError("missing XICE_AUDIT_DB_PASSWORD")
    return psycopg2.connect(
        host=AUDIT_DB_HOST,
        port=AUDIT_DB_PORT,
        dbname=AUDIT_DB_NAME,
        user=AUDIT_DB_USER,
        password=AUDIT_DB_PASSWORD,
        connect_timeout=3,
    )


def init_web_tables():
    with db_connect() as conn, conn.cursor() as cur:
        cur.execute(
            """
            CREATE TABLE IF NOT EXISTS web_players (
              player_uuid TEXT PRIMARY KEY,
              player_name TEXT NOT NULL,
              registered_at BIGINT NOT NULL,
              updated_at BIGINT NOT NULL
            )
            """
        )


def ensure_web_player(entry):
    now = int(time.time() * 1000)
    try:
        with db_connect() as conn, conn.cursor() as cur:
            cur.execute(
                """
                INSERT INTO web_players (player_uuid, player_name, registered_at, updated_at)
                VALUES (%s, %s, %s, %s)
                ON CONFLICT (player_uuid)
                DO UPDATE SET player_name = EXCLUDED.player_name, updated_at = EXCLUDED.updated_at
                """,
                (entry["uuid"], entry["name"], now, now),
            )
    except Exception as exc:
        print(f"failed to ensure web player: {exc}")


def find_verification_code(username, code):
    now = int(time.time() * 1000)
    username_key = username.lower()
    submitted_code = code.strip().upper()
    entries = read_verification_codes(now)
    for entry in entries:
        if entry["key"] == username_key and hmac.compare_digest(entry["code"].upper(), submitted_code):
            return {"name": entry["name"], "uuid": canonical_uuid(entry["uuid"])}
    return None


def consume_verification_code(username, code):
    username_key = username.lower()
    submitted_code = code.strip().upper()
    kept = [
        entry for entry in read_verification_codes()
        if not (entry["key"] == username_key and hmac.compare_digest(entry["code"].upper(), submitted_code))
    ]
    write_verification_codes(kept)


def read_verification_codes(now_ms=None):
    now_ms = now_ms or int(time.time() * 1000)
    entries = []
    try:
        with open(VERIFY_CODES_PATH, "r", encoding="utf-8") as file:
            for line in file:
                if not line.strip() or line.startswith("#"):
                    continue
                parts = line.rstrip("\n").split("\t")
                if len(parts) != 5:
                    continue
                try:
                    expires_at = int(parts[4])
                except ValueError:
                    continue
                if expires_at <= now_ms:
                    continue
                entries.append({
                    "key": parts[0],
                    "uuid": parts[1],
                    "name": parts[2],
                    "code": parts[3],
                    "expires_at": expires_at,
                })
    except FileNotFoundError:
        return []
    return entries


def write_verification_codes(entries):
    os.makedirs(os.path.dirname(VERIFY_CODES_PATH), exist_ok=True)
    temp_path = VERIFY_CODES_PATH + ".tmp"
    with open(temp_path, "w", encoding="utf-8") as file:
        file.write("# key\tuuid\tplayer\tcode\texpiresAtMillis\n")
        for entry in entries:
            file.write("\t".join([
                entry["key"],
                entry["uuid"],
                entry["name"],
                entry["code"],
                str(entry["expires_at"]),
            ]) + "\n")
    os.replace(temp_path, VERIFY_CODES_PATH)


def format_time_ms(value):
    if not value:
        return "暂无记录"
    return time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(int(value) / 1000))


def format_duration(seconds):
    seconds = int(seconds or 0)
    hours, remainder = divmod(seconds, 3600)
    minutes, seconds = divmod(remainder, 60)
    return f"{hours}小时 {minutes}分 {seconds}秒"


def esc(value):
    return html.escape(str(value), quote=True)


def page(title, body, status=HTTPStatus.OK, user=None, active="home"):
    escaped_title = esc(title)
    nav = ""
    shell_class = "login-shell" if user is None else "app-shell"
    if user:
        nav = f"""
<aside class="sidebar">
  <div class="brand">XiceMCServer</div>
  <div class="user">{esc(user["name"])}</div>
  <nav>
    <a class="{ 'active' if active == 'home' else '' }" href="/home">首页</a>
    <a class="{ 'active' if active == 'status' else '' }" href="/status">服务器状态</a>
    <a class="{ 'active' if active == 'audit' else '' }" href="/audit">操作查询</a>
    <a class="{ 'active' if active == 'report' else '' }" href="/report">举报</a>
  </nav>
  <form method="post" action="/logout"><button class="secondary" type="submit">退出登录</button></form>
</aside>
"""
    return status, f"""<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>{escaped_title}</title>
  <style>
    :root {{
      color-scheme: light;
      --bg: #ffffff;
      --panel: #ffffff;
      --line: #d8e0ea;
      --muted: #607084;
      --text: #142033;
      --accent: #1f6feb;
      --accent2: #45c48b;
      --danger: #c24141;
    }}
    * {{ box-sizing: border-box; }}
    body {{
      margin: 0;
      min-height: 100vh;
      font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      background: var(--bg);
      color: var(--text);
    }}
    a {{ color: #1f6feb; text-decoration: none; }}
    h1 {{ margin: 0 0 18px; font-size: 24px; }}
    h2 {{ margin: 0 0 12px; font-size: 18px; }}
    p {{ line-height: 1.6; color: var(--muted); }}
    .login-shell {{
      min-height: 100vh;
      display: grid;
      place-items: center;
      padding: 20px;
    }}
    .login-card {{
      width: min(92vw, 440px);
      padding: 28px;
      border: 1px solid var(--line);
      border-radius: 8px;
      background: var(--panel);
    }}
    .app-shell {{
      min-height: 100vh;
      display: grid;
      grid-template-columns: 240px 1fr;
    }}
    .sidebar {{
      border-right: 1px solid var(--line);
      padding: 22px;
      background: #ffffff;
    }}
    .brand {{ font-weight: 700; margin-bottom: 8px; }}
    .user {{ color: var(--muted); margin-bottom: 22px; }}
    nav {{ display: grid; gap: 8px; margin-bottom: 24px; }}
    nav a {{
      display: block;
      padding: 10px 12px;
      border-radius: 6px;
      color: var(--text);
    }}
    nav a.active, nav a:hover {{ background: #eaf1ff; }}
    main {{ padding: 28px; width: 100%; min-width: 0; }}
    .panel {{
      border: 1px solid var(--line);
      border-radius: 8px;
      background: var(--panel);
      padding: 20px;
      margin-bottom: 18px;
    }}
    .grid {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 14px; }}
    .audit-grid {{
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      gap: 14px;
      align-items: end;
    }}
    .coordinate-group {{
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 8px;
    }}
    .is-hidden {{ display: none; }}
    .stat {{
      border: 1px solid var(--line);
      border-radius: 6px;
      padding: 14px;
      background: #f8fafc;
    }}
    .stat .label {{ color: var(--muted); font-size: 13px; }}
    .stat .value {{ margin-top: 8px; font-size: 16px; overflow-wrap: anywhere; }}
    label {{ display: block; margin: 12px 0 6px; color: var(--muted); }}
    input, select {{
      width: 100%;
      padding: 10px 11px;
      border-radius: 6px;
      border: 1px solid #c7d2df;
      background: #ffffff;
      color: var(--text);
      font-size: 15px;
    }}
    button, .button {{
      display: inline-block;
      border: 0;
      border-radius: 6px;
      padding: 10px 13px;
      background: var(--accent);
      color: white;
      font-size: 15px;
      cursor: pointer;
      text-align: center;
    }}
    button.secondary, .button.secondary {{ background: #e7edf5; color: var(--text); }}
    .actions {{ display: flex; gap: 10px; flex-wrap: wrap; margin-top: 16px; }}
    .divider {{ border-top: 1px solid var(--line); margin: 18px 0; }}
    .message {{ color: var(--muted); }}
    .error {{ color: var(--danger); }}
    .mono {{ font-family: ui-monospace, SFMono-Regular, Consolas, "Liberation Mono", monospace; font-size: 13px; }}
    .log-lines {{ white-space: pre-wrap; overflow-wrap: anywhere; }}
    .table-wrap {{ width: 100%; overflow-x: auto; }}
    table {{ width: 100%; min-width: 100%; border-collapse: collapse; font-size: 14px; }}
    th, td {{ padding: 10px; border-bottom: 1px solid var(--line); text-align: left; vertical-align: top; }}
    th {{ color: var(--muted); font-weight: 600; }}
    .nowrap {{ white-space: nowrap; }}
    @media (max-width: 760px) {{
      .app-shell {{ grid-template-columns: 1fr; }}
      .sidebar {{ border-right: 0; border-bottom: 1px solid var(--line); }}
      main {{ padding: 18px; }}
    }}
  </style>
</head>
<body>
  <div class="{shell_class}">
    {nav}
    <main>{body}</main>
  </div>
</body>
</html>"""


def redirect(location, cookie=None):
    headers = [("Location", location)]
    if cookie:
        headers.append(("Set-Cookie", cookie))
    return HTTPStatus.SEE_OTHER, "", headers


def login_page(message="", status=HTTPStatus.OK):
    safe_message = f'<p class="message">{esc(message)}</p>' if message else ""
    body = f"""
<section class="login-card">
  <h1>XiceMCServer 登录</h1>
  {safe_message}
  <form method="post" action="/login">
    <label for="username">Minecraft Java 版 ID</label>
    <input id="username" name="username" autocomplete="username" required minlength="3" maxlength="16" pattern="[A-Za-z0-9_]+">
    <div class="actions">
      <button type="submit">登录</button>
      <a class="button secondary" href="/register">注册白名单</a>
    </div>
  </form>
</section>
"""
    return page("XiceMCServer 登录", body, status)


def register_page(message="", status=HTTPStatus.OK):
    safe_message = f'<p class="message">{esc(message)}</p>' if message else ""
    body = f"""
<section class="login-card">
  <h1>注册白名单</h1>
  {safe_message}
  <form method="post" action="/register">
    <label for="username">Minecraft Java 版 ID</label>
    <input id="username" name="username" autocomplete="username" required minlength="3" maxlength="16" pattern="[A-Za-z0-9_]+">
    <label for="verification_code">验证码</label>
    <input id="verification_code" name="verification_code" autocomplete="off" required minlength="4" maxlength="16">
    <div class="actions">
      <button type="submit">加入白名单</button>
      <a class="button secondary" href="/">返回登录</a>
    </div>
  </form>
</section>
"""
    return page("注册白名单", body, status)


def home_page(user):
    stats = load_player_stats(user)
    body = f"""
<h1>首页</h1>
<section class="panel">
  <h2>个人信息</h2>
  <div class="grid">
    <div class="stat"><div class="label">玩家 ID</div><div class="value">{esc(user["name"])}</div></div>
    <div class="stat"><div class="label">用户 UUID</div><div class="value">{esc(user["uuid"])}</div></div>
    <div class="stat"><div class="label">注册时间</div><div class="value">{esc(stats["registered_at"])}</div></div>
    <div class="stat"><div class="label">累计游玩时间</div><div class="value">{esc(stats["play_time"])}</div></div>
    <div class="stat"><div class="label">上次登出地点</div><div class="value">{esc(stats["last_logout"])}</div></div>
  </div>
</section>
"""
    return page("首页", body, user=user, active="home")


def report_page(user):
    body = """
<h1>举报</h1>
<section class="panel">
</section>
"""
    return page("举报", body, user=user, active="report")


def status_page(user):
    status = load_server_status()
    backups_html = "".join(
        f"<tr><td>{esc(item['name'])}</td><td>{esc(item['size'])}</td><td>{esc(item['mtime'])}</td></tr>"
        for item in status["backups"]
    )
    if not backups_html:
        backups_html = '<tr><td colspan="3" class="message">暂无备份文件</td></tr>'
    error_lines = "\n".join(status["log_errors"]) if status["log_errors"] else "最近日志未发现 ERROR / Exception / SEVERE。"
    body = f"""
<h1>服务器状态</h1>
<section class="panel">
  <div class="grid">
    <div class="stat"><div class="label">开服状态</div><div class="value">{esc(status["server_state"])}</div></div>
    <div class="stat"><div class="label">在线玩家</div><div class="value">{esc(status["online_players"])}</div></div>
    <div class="stat"><div class="label">磁盘空间</div><div class="value">{esc(status["disk"])}</div></div>
    <div class="stat"><div class="label">内存占用</div><div class="value">{esc(status["memory"])}</div></div>
  </div>
</section>
<section class="panel">
  <h2>备份文件</h2>
  <div class="table-wrap">
    <table>
      <thead><tr><th>文件名</th><th>大小</th><th>修改时间</th></tr></thead>
      <tbody>{backups_html}</tbody>
    </table>
  </div>
</section>
<section class="panel">
  <h2>日志 ERROR 情况</h2>
  <p class="message">最近 {esc(status["log_scan_lines"])} 行日志，匹配 ERROR / Exception / SEVERE 共 {esc(status["log_error_count"])} 行。</p>
  <div class="mono log-lines">{esc(error_lines)}</div>
</section>
"""
    return page("服务器状态", body, user=user, active="status")


def load_player_stats(user):
    result = {
        "registered_at": "暂无记录",
        "play_time": "暂无记录",
        "last_logout": "暂无记录",
    }
    try:
        with db_connect() as conn, conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute("SELECT registered_at FROM web_players WHERE player_uuid = %s", (user["uuid"],))
            row = cur.fetchone()
            if row:
                result["registered_at"] = format_time_ms(row["registered_at"])

            cur.execute(
                "SELECT COALESCE(SUM(item_amount), 0) AS seconds FROM audit_log WHERE player_uuid = %s AND action = 'PLAYER_QUIT'",
                (user["uuid"],),
            )
            row = cur.fetchone()
            result["play_time"] = format_duration(row["seconds"] if row else 0)

            cur.execute(
                """
                SELECT world, x, y, z, created_at
                FROM audit_log
                WHERE player_uuid = %s AND action = 'PLAYER_QUIT'
                ORDER BY created_at DESC, id DESC
                LIMIT 1
                """,
                (user["uuid"],),
            )
            row = cur.fetchone()
            if row:
                result["last_logout"] = f'{row["world"]} ({row["x"]}, {row["y"]}, {row["z"]}) / {format_time_ms(row["created_at"])}'
    except Exception as exc:
        result["registered_at"] = f"数据库暂不可用：{exc}"
    return result


def load_server_status():
    errors = log_error_lines()
    return {
        "server_state": server_state(),
        "online_players": online_player_summary(),
        "disk": disk_summary(),
        "memory": memory_summary(),
        "backups": backup_files(),
        "log_errors": errors[-10:],
        "log_error_count": len(errors),
        "log_scan_lines": "500",
    }


def server_state():
    result = run_command(["systemctl", "is-active", SERVER_SERVICE_NAME])
    if result["ok"]:
        state = result["stdout"].strip()
        return "运行中" if state == "active" else state
    try:
        RconClient(RCON_HOST, RCON_PORT, RCON_PASSWORD, timeout=3).run("list")
        return "运行中"
    except Exception:
        return "未运行或无法连接"


def online_player_summary():
    try:
        output = RconClient(RCON_HOST, RCON_PORT, RCON_PASSWORD, timeout=3).run("list")
        return output or "暂无在线玩家"
    except Exception as exc:
        return f"无法读取：{exc}"


def disk_summary():
    try:
        usage = shutil.disk_usage(RUNTIME_DIR if os.path.exists(RUNTIME_DIR) else "/")
        used = usage.total - usage.free
        percent = used / usage.total * 100 if usage.total else 0
        return f"{format_bytes(used)} / {format_bytes(usage.total)} ({percent:.1f}%)"
    except Exception as exc:
        return f"无法读取：{exc}"


def memory_summary():
    try:
        meminfo = {}
        with open("/proc/meminfo", "r", encoding="utf-8") as file:
            for line in file:
                key, value = line.split(":", 1)
                meminfo[key] = int(value.strip().split()[0]) * 1024
        total = meminfo.get("MemTotal", 0)
        available = meminfo.get("MemAvailable", 0)
        used = total - available
        percent = used / total * 100 if total else 0
        return f"{format_bytes(used)} / {format_bytes(total)} ({percent:.1f}%)"
    except FileNotFoundError:
        return "当前系统不支持 /proc/meminfo"
    except Exception as exc:
        return f"无法读取：{exc}"


def backup_files(limit=20):
    if not os.path.isdir(BACKUP_DIR):
        return []
    files = []
    for name in os.listdir(BACKUP_DIR):
        path = os.path.join(BACKUP_DIR, name)
        if not os.path.isfile(path) or not name.endswith(".tar.gz"):
            continue
        stat = os.stat(path)
        files.append(
            {
                "name": name,
                "size": format_bytes(stat.st_size),
                "mtime": time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(stat.st_mtime)),
            }
        )
    files.sort(key=lambda item: item["mtime"], reverse=True)
    return files[:limit]


def log_error_lines(scan_lines=500):
    lines = tail_lines(SERVER_LOG_PATH, scan_lines)
    matched = [line for line in lines if log_line_is_error(line)]
    return matched


def log_line_is_error(line):
    upper = line.upper()
    return "ERROR" in upper or "EXCEPTION" in upper or "SEVERE" in upper


def tail_lines(path, limit):
    try:
        with open(path, "r", encoding="utf-8", errors="replace") as file:
            lines = file.readlines()
        return [line.rstrip("\n") for line in lines[-limit:]]
    except FileNotFoundError:
        return []
    except Exception as exc:
        return [f"无法读取日志：{exc}"]


def run_command(args, timeout=3):
    try:
        result = subprocess.run(args, capture_output=True, text=True, timeout=timeout, check=False)
        return {"ok": result.returncode == 0, "stdout": result.stdout, "stderr": result.stderr}
    except Exception as exc:
        return {"ok": False, "stdout": "", "stderr": str(exc)}


def format_bytes(value):
    value = float(value or 0)
    for unit in ("B", "KB", "MB", "GB", "TB"):
        if value < 1024 or unit == "TB":
            return f"{value:.1f} {unit}" if unit != "B" else f"{int(value)} B"
        value /= 1024


def audit_page(user, params):
    rows, next_cursor, message = query_audit(params)
    query = {key: params.get(key, [""])[0] for key in params}
    container_options_html = container_options(query.get("target_type", ""))
    min_audit_time = format_datetime_local(audit_cutoff_ms())
    max_audit_time = format_datetime_local(int(time.time() * 1000))
    body = f"""
<h1>操作查询</h1>
<section class="panel">
  <form method="get" action="/audit">
    <input type="hidden" name="submitted" value="1">
    <div class="audit-grid">
      <div>
        <label for="action">操作</label>
        <select id="action" name="action">
          {option("", "请选择", query.get("action"))}
          {option("BLOCK_PLACE", "放置方块", query.get("action"))}
          {option("BLOCK_BREAK", "破坏方块", query.get("action"))}
          {option("CONTAINER_ADD", "存入物品", query.get("action"))}
          {option("CONTAINER_REMOVE", "取出物品", query.get("action"))}
          {option("PLAYER_JOIN", "玩家进入", query.get("action"))}
          {option("PLAYER_QUIT", "玩家退出", query.get("action"))}
        </select>
      </div>
      <div>
        <label for="player">操作来源：玩家名或 UUID</label>
        <input id="player" name="player" value="{esc(query.get("player", ""))}" placeholder="例如 ExamplePlayer">
      </div>
      <div id="block-target-field">
        <label for="block_target_type">方块类型</label>
        <input id="block_target_type" name="target_type" value="{esc(query.get("target_type", ""))}" placeholder="例如 OBSIDIAN">
      </div>
      <div id="container-target-field">
        <label for="container_target_type">容器类型</label>
        <select id="container_target_type" name="target_type">
          {container_options_html}
        </select>
      </div>
      <div id="item-type-field">
        <label for="item_type">物品类型</label>
        <input id="item_type" name="item_type" value="{esc(query.get("item_type", ""))}" placeholder="例如 DIAMOND">
      </div>
      <div>
        <label for="time_from">开始时间</label>
        <input id="time_from" name="time_from" type="datetime-local" min="{esc(min_audit_time)}" max="{esc(max_audit_time)}" value="{esc(query.get("time_from", ""))}">
      </div>
      <div>
        <label for="time_to">结束时间</label>
        <input id="time_to" name="time_to" type="datetime-local" min="{esc(min_audit_time)}" max="{esc(max_audit_time)}" value="{esc(query.get("time_to", ""))}">
      </div>
      <div>
        <label for="world">世界</label>
        <input id="world" name="world" value="{esc(query.get("world", ""))}" placeholder="main">
      </div>
      <div>
        <label>坐标</label>
        <div class="coordinate-group">
          <input id="x" name="x" value="{esc(query.get("x", ""))}" inputmode="numeric" placeholder="X">
          <input id="y" name="y" value="{esc(query.get("y", ""))}" inputmode="numeric" placeholder="Y">
          <input id="z" name="z" value="{esc(query.get("z", ""))}" inputmode="numeric" placeholder="Z">
        </div>
      </div>
      <div>
        <label for="radius">半径，最大 {MAX_RADIUS}</label>
        <input id="radius" name="radius" value="{esc(query.get("radius", ""))}" inputmode="numeric" placeholder="10">
      </div>
      <div>
        <label>每页数量</label>
        <input value="{QUERY_LIMIT}" disabled>
      </div>
    </div>
    <div class="actions">
      <button type="submit">查询</button>
      <a class="button secondary" href="/audit">清空</a>
    </div>
  </form>
</section>
<section class="panel">
  {render_audit_results(rows, next_cursor, query, message)}
</section>
<script>
  const actionSelect = document.getElementById("action");
  const blockTargetField = document.getElementById("block-target-field");
  const blockTargetInput = document.getElementById("block_target_type");
  const containerTargetField = document.getElementById("container-target-field");
  const containerTargetSelect = document.getElementById("container_target_type");
  const itemTypeField = document.getElementById("item-type-field");
  const itemTypeInput = document.getElementById("item_type");

  function setFieldVisible(field, control, visible) {{
    field.classList.toggle("is-hidden", !visible);
    control.disabled = !visible;
  }}

  function refreshAuditFields() {{
    const action = actionSelect.value;
    const blockAction = action === "BLOCK_PLACE" || action === "BLOCK_BREAK";
    const containerAction = action === "CONTAINER_ADD" || action === "CONTAINER_REMOVE";
    setFieldVisible(blockTargetField, blockTargetInput, blockAction);
    setFieldVisible(containerTargetField, containerTargetSelect, containerAction);
    setFieldVisible(itemTypeField, itemTypeInput, containerAction);
  }}

  actionSelect.addEventListener("change", refreshAuditFields);
  refreshAuditFields();
</script>
"""
    return page("操作查询", body, user=user, active="audit")


def option(value, label, selected):
    attr = " selected" if value == (selected or "") else ""
    return f'<option value="{esc(value)}"{attr}>{esc(label)}</option>'


def container_options(selected):
    values = {
        "",
        "CHEST",
        "BARREL",
        "SHULKER_BOX",
        "FURNACE",
        "BLAST_FURNACE",
        "SMOKER",
        "HOPPER",
        "DROPPER",
        "DISPENSER",
    }
    try:
        with db_connect() as conn, conn.cursor() as cur:
            cur.execute(
                """
                SELECT DISTINCT target_type
                FROM audit_log
                WHERE action IN ('CONTAINER_ADD', 'CONTAINER_REMOVE')
                  AND target_type IS NOT NULL
                  AND target_type <> ''
                ORDER BY target_type
                LIMIT 300
                """
            )
            values.update(row[0] for row in cur.fetchall())
    except Exception:
        pass
    if selected:
        values.add(selected)
    ordered = [""] + sorted(value for value in values if value)
    labels = {"": "不限"}
    return "\n".join(option(value, labels.get(value, value), selected) for value in ordered)


def render_audit_results(rows, next_cursor, query, message):
    if message:
        css = "error" if message.startswith("错误") else "message"
        return f'<p class="{css}">{esc(message)}</p>'
    if not rows:
        return '<p class="message">没有查询到记录。</p>'

    table_rows = []
    for row in rows:
        table_rows.append(
            "<tr>"
            f'<td class="nowrap">{esc(format_time_ms(row["created_at"]))}</td>'
            f"<td>{esc(row['action'])}</td>"
            f"<td>{esc(row['player_name'])}</td>"
            f"<td>{esc(row['world'])}<br>{esc(row['x'])}, {esc(row['y'])}, {esc(row['z'])}</td>"
            f"<td>{esc(row['target_type'] or '')}</td>"
            f"<td>{esc(row['item_type'] or '')}</td>"
            f"<td>{esc(row['item_amount'])}</td>"
            "</tr>"
        )
    next_link = ""
    if next_cursor:
        next_query = {key: value for key, value in query.items() if value}
        next_query["cursor"] = next_cursor
        next_link = f'<div class="actions"><a class="button secondary" href="/audit?{esc(urlencode(next_query))}">下一页</a></div>'
    return f"""
<div class="table-wrap">
<table>
  <thead><tr><th>时间</th><th>操作</th><th>来源</th><th>位置</th><th>对象类型</th><th>物品</th><th>数量/秒数</th></tr></thead>
  <tbody>{''.join(table_rows)}</tbody>
</table>
</div>
{next_link}
"""


def query_audit(params):
    submitted = first(params, "submitted") == "1"
    if not submitted and not first(params, "cursor"):
        return [], None, "请先设置筛选条件。默认不会展示任何审计记录。"
    try:
        where, values = build_audit_filters(params)
        sql = """
            SELECT id, created_at, action, player_uuid, player_name, world, x, y, z,
                   target_type, item_type, item_amount
            FROM audit_log
            WHERE {where}
            ORDER BY created_at DESC, id DESC
            LIMIT %s
        """.format(where=" AND ".join(where))
        values.append(QUERY_LIMIT + 1)
        with db_connect() as conn, conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute(sql, values)
            rows = cur.fetchall()
        next_cursor = None
        if len(rows) > QUERY_LIMIT:
            last = rows[QUERY_LIMIT - 1]
            next_cursor = b64url(json.dumps({"t": last["created_at"], "id": last["id"]}).encode("utf-8"))
            rows = rows[:QUERY_LIMIT]
        return rows, next_cursor, ""
    except Exception as exc:
        return [], None, f"错误：{exc}"


def build_audit_filters(params):
    where = []
    values = []
    cutoff = audit_cutoff_ms()
    now_ms = int(time.time() * 1000)

    action = first(params, "action").upper()
    allowed_actions = {"BLOCK_PLACE", "BLOCK_BREAK", "CONTAINER_ADD", "CONTAINER_REMOVE", "PLAYER_JOIN", "PLAYER_QUIT"}
    if action:
        if action not in allowed_actions:
            raise ValueError("未知操作类型")
        where.append("action = %s")
        values.append(action)

    player = first(params, "player")
    if player:
        if UUID_RE.fullmatch(player):
            where.append("player_uuid = %s")
        else:
            where.append("player_name = %s")
        values.append(player)

    block_actions = {"BLOCK_PLACE", "BLOCK_BREAK"}
    container_actions = {"CONTAINER_ADD", "CONTAINER_REMOVE"}

    target_type = first(params, "target_type").upper()
    if target_type and (not action or action in block_actions or action in container_actions):
        where.append("target_type = %s")
        values.append(target_type)

    item_type = first(params, "item_type").upper()
    if item_type and (not action or action in container_actions):
        where.append("item_type = %s")
        values.append(item_type)

    time_from = parse_datetime_local(first(params, "time_from"))
    if time_from is not None:
        if time_from < cutoff:
            raise ValueError(f"开始时间只能选择最近 {AUDIT_RETENTION_DAYS} 天内")
        if time_from > now_ms:
            raise ValueError("开始时间不能晚于当前时间")
        where.append("created_at >= %s")
        values.append(time_from)
    else:
        where.append("created_at >= %s")
        values.append(cutoff)

    time_to = parse_datetime_local(first(params, "time_to"))
    if time_to is not None:
        if time_to < cutoff:
            raise ValueError(f"结束时间只能选择最近 {AUDIT_RETENTION_DAYS} 天内")
        if time_to > now_ms:
            raise ValueError("结束时间不能晚于当前时间")
        if time_from is not None and time_to < time_from:
            raise ValueError("结束时间不能早于开始时间")
        where.append("created_at <= %s")
        values.append(time_to)

    world = first(params, "world")
    coords = [first(params, key) for key in ("x", "y", "z")]
    if world or any(coords):
        if not world or not all(coords):
            raise ValueError("范围查询需要同时填写世界、X、Y、Z")
        x, y, z = [int(value) for value in coords]
        radius = clamp_int(first(params, "radius"), 10, 0, MAX_RADIUS)
        where.append("world = %s")
        values.append(world)
        where.append("x BETWEEN %s AND %s")
        values.extend([x - radius, x + radius])
        where.append("y BETWEEN %s AND %s")
        values.extend([y - radius, y + radius])
        where.append("z BETWEEN %s AND %s")
        values.extend([z - radius, z + radius])

    cursor = first(params, "cursor")
    if cursor:
        decoded = json.loads(b64url_decode(cursor).decode("utf-8"))
        where.append("(created_at < %s OR (created_at = %s AND id < %s))")
        values.extend([int(decoded["t"]), int(decoded["t"]), int(decoded["id"])])

    if not where:
        where.append("TRUE")
    return where, values


def parse_datetime_local(value):
    if not value:
        return None
    return int(datetime.fromisoformat(value).timestamp() * 1000)


def audit_cutoff_ms():
    return int((time.time() - AUDIT_RETENTION_DAYS * 86400) * 1000)


def format_datetime_local(value_ms):
    return time.strftime("%Y-%m-%dT%H:%M", time.localtime(int(value_ms) / 1000))


def first(params, key):
    return params.get(key, [""])[0].strip()


def clamp_int(value, default, minimum, maximum):
    if value == "":
        return default
    number = int(value)
    return max(minimum, min(maximum, number))


class Handler(BaseHTTPRequestHandler):
    server_version = "XiceWeb/0.2"

    def log_message(self, fmt, *args):
        print(f"{self.address_string()} - {fmt % args}")

    def do_GET(self):
        parsed = urlparse(self.path)
        user = parse_session(self.headers.get("Cookie"))

        if parsed.path == "/":
            if user:
                self.send_redirect("/home")
            else:
                self.respond(*login_page())
            return
        if parsed.path == "/register":
            self.respond(*register_page())
            return
        if parsed.path == "/home":
            if not user:
                self.send_redirect("/")
                return
            self.respond(*home_page(user))
            return
        if parsed.path == "/report":
            if not user:
                self.send_redirect("/")
                return
            self.respond(*report_page(user))
            return
        if parsed.path == "/status":
            if not user:
                self.send_redirect("/")
                return
            self.respond(*status_page(user))
            return
        if parsed.path == "/audit":
            if not user:
                self.send_redirect("/")
                return
            self.respond(*audit_page(user, parse_qs(parsed.query)))
            return

        self.respond(*page("未找到", "<h1>未找到</h1>", HTTPStatus.NOT_FOUND, user=user))

    def do_POST(self):
        parsed = urlparse(self.path)
        if parsed.path == "/login":
            self.handle_login()
            return
        if parsed.path == "/logout":
            self.send_redirect("/", "xicemc_session=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax")
            return
        if parsed.path == "/register":
            self.handle_register()
            return
        self.respond(*page("未找到", "<h1>未找到</h1>", HTTPStatus.NOT_FOUND))

    def read_form(self, max_length=2048):
        length = int(self.headers.get("Content-Length", "0"))
        if length > max_length:
            raise ValueError("请求内容过大")
        data = self.rfile.read(length).decode("utf-8", "replace")
        return parse_qs(data)

    def handle_login(self):
        try:
            params = self.read_form()
            username = first(params, "username")
            if not USERNAME_RE.fullmatch(username):
                self.respond(*login_page("Minecraft ID 格式不正确。", HTTPStatus.BAD_REQUEST))
                return
            entry = whitelist_entry(username)
            if not entry:
                self.respond(*login_page("该玩家不在白名单内，请先注册白名单。", HTTPStatus.FORBIDDEN))
                return
            ensure_web_player(entry)
            self.send_redirect("/home", f"xicemc_session={make_session(entry)}; Path=/; Max-Age={SESSION_SECONDS}; HttpOnly; SameSite=Lax")
        except Exception as exc:
            self.respond(*login_page(f"登录失败：{exc}", HTTPStatus.INTERNAL_SERVER_ERROR))

    def handle_register(self):
        ip = self.client_address[0]
        if rate_limited(ip):
            self.respond(*register_page("请求过于频繁，请稍后再试。", HTTPStatus.TOO_MANY_REQUESTS))
            return

        try:
            params = self.read_form()
            username = first(params, "username")
            verification_code = first(params, "verification_code")
        except ValueError as exc:
            self.respond(*register_page(str(exc), HTTPStatus.BAD_REQUEST))
            return

        if not USERNAME_RE.fullmatch(username):
            self.respond(*register_page("Minecraft ID 格式不正确。", HTTPStatus.BAD_REQUEST))
            return

        verified_entry = find_verification_code(username, verification_code)
        if not verified_entry:
            self.respond(*register_page("验证码不正确或已过期。请重新进入服务器获取新的验证码。", HTTPStatus.FORBIDDEN))
            return

        try:
            added = add_whitelist_entry(verified_entry)
            result = reload_whitelist()
            entry = whitelist_entry_by_uuid(verified_entry["uuid"]) or whitelist_entry(verified_entry["name"])
            if entry:
                ensure_web_player(entry)
            consume_verification_code(username, verification_code)
        except Exception as exc:
            self.respond(*register_page(f"白名单服务暂时不可用：{exc}", HTTPStatus.INTERNAL_SERVER_ERROR))
            return

        body = f"""
<section class="login-card">
  <h1>已提交白名单</h1>
  <p>玩家 <strong>{esc(verified_entry["name"])}</strong> 已提交加入白名单。</p>
  <p>{esc("已加入白名单。" if added else "该玩家已在白名单内，已刷新白名单。")}</p>
  <p>{esc(result or "白名单已刷新。")}</p>
  <div class="actions"><a class="button" href="/">返回登录</a></div>
</section>
"""
        self.respond(*page("已提交白名单", body))

    def send_redirect(self, location, cookie=None):
        headers = [("Location", location)]
        if cookie:
            if isinstance(cookie, list):
                headers.extend(("Set-Cookie", value) for value in cookie)
            else:
                headers.append(("Set-Cookie", cookie))
        self.respond(HTTPStatus.SEE_OTHER, "", extra_headers=headers)

    def respond(self, status, content, extra_headers=None):
        encoded = content.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.send_header("Cache-Control", "no-store")
        if extra_headers:
            for name, value in extra_headers:
                self.send_header(name, value)
        self.end_headers()
        self.wfile.write(encoded)


def main():
    if psycopg2 is not None and AUDIT_DB_PASSWORD:
        try:
            init_web_tables()
        except Exception as exc:
            print(f"Failed to initialize web tables: {exc}")
    httpd = ThreadingHTTPServer((HOST, PORT), Handler)
    print(f"Xice web listening on {HOST}:{PORT}")
    httpd.serve_forever()


if __name__ == "__main__":
    main()
