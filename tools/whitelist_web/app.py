#!/usr/bin/env python3
import base64
import hashlib
import hmac
import html
import json
import os
import re
import socket
import struct
import time
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
MAX_QUERY_LIMIT = 100
DEFAULT_QUERY_LIMIT = 50
MAX_RADIUS = 200
ATTEMPTS = {}


def env(name, default=None, required=False):
    value = os.environ.get(name, default)
    if required and not value:
        raise RuntimeError(f"Missing required environment variable: {name}")
    return value


HOST = env("WHITELIST_WEB_HOST", "0.0.0.0")
PORT = int(env("WHITELIST_WEB_PORT", "80"))
INVITE_CODE = env("WHITELIST_INVITE_CODE", required=True)
RCON_HOST = env("XICEMC_RCON_HOST", "127.0.0.1")
RCON_PORT = int(env("XICEMC_RCON_PORT", "25575"))
RCON_PASSWORD = env("XICEMC_RCON_PASSWORD", required=True)
RUNTIME_DIR = env("XICEMC_RUNTIME_DIR", "/opt/xicemc/runtime")
WHITELIST_PATH = env("XICEMC_WHITELIST_PATH", os.path.join(RUNTIME_DIR, "whitelist.json"))
SESSION_SECRET = env("WHITELIST_WEB_SESSION_SECRET", INVITE_CODE + RCON_PASSWORD, required=True)
AUDIT_DB_HOST = env("XICE_AUDIT_DB_HOST", "127.0.0.1")
AUDIT_DB_PORT = int(env("XICE_AUDIT_DB_PORT", "5432"))
AUDIT_DB_NAME = env("XICE_AUDIT_DB_NAME", "xicemc_audit")
AUDIT_DB_USER = env("XICE_AUDIT_DB_USER", "xicemc_audit")
AUDIT_DB_PASSWORD = env("XICE_AUDIT_DB_PASSWORD", "")


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


def read_whitelist():
    try:
        with open(WHITELIST_PATH, "r", encoding="utf-8") as file:
            entries = json.load(file)
    except FileNotFoundError:
        return {}
    return {entry.get("name", "").lower(): entry for entry in entries if entry.get("name") and entry.get("uuid")}


def whitelist_entry(username):
    return read_whitelist().get(username.lower())


def b64url(data):
    return base64.urlsafe_b64encode(data).decode("ascii").rstrip("=")


def b64url_decode(value):
    padding = "=" * (-len(value) % 4)
    return base64.urlsafe_b64decode(value + padding)


def sign(value):
    return hmac.new(SESSION_SECRET.encode("utf-8"), value.encode("utf-8"), hashlib.sha256).hexdigest()


def make_session(entry):
    payload = {
        "uuid": entry["uuid"],
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
    entry = whitelist_entry(payload.get("name", ""))
    if not entry or entry.get("uuid") != payload.get("uuid"):
        return None
    return {"uuid": entry["uuid"], "name": entry["name"]}


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
    <a class="{ 'active' if active == 'audit' else '' }" href="/audit">操作查询</a>
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
      color-scheme: dark;
      --bg: #0f141a;
      --panel: #171d24;
      --line: #2b3542;
      --muted: #aab7c4;
      --text: #edf2f7;
      --accent: #2f8cff;
      --accent2: #45c48b;
      --danger: #ff6b6b;
    }}
    * {{ box-sizing: border-box; }}
    body {{
      margin: 0;
      min-height: 100vh;
      font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      background: var(--bg);
      color: var(--text);
    }}
    a {{ color: #8cc8ff; text-decoration: none; }}
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
      background: #121820;
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
    nav a.active, nav a:hover {{ background: #202a36; }}
    main {{ padding: 28px; max-width: 1220px; width: 100%; }}
    .panel {{
      border: 1px solid var(--line);
      border-radius: 8px;
      background: var(--panel);
      padding: 20px;
      margin-bottom: 18px;
    }}
    .grid {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 14px; }}
    .stat {{
      border: 1px solid var(--line);
      border-radius: 6px;
      padding: 14px;
      background: #121820;
    }}
    .stat .label {{ color: var(--muted); font-size: 13px; }}
    .stat .value {{ margin-top: 8px; font-size: 16px; overflow-wrap: anywhere; }}
    label {{ display: block; margin: 12px 0 6px; color: var(--muted); }}
    input, select {{
      width: 100%;
      padding: 10px 11px;
      border-radius: 6px;
      border: 1px solid #3b4654;
      background: #0f141a;
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
    button.secondary, .button.secondary {{ background: #2b3542; }}
    .actions {{ display: flex; gap: 10px; flex-wrap: wrap; margin-top: 16px; }}
    .message {{ color: var(--muted); }}
    .error {{ color: var(--danger); }}
    table {{ width: 100%; border-collapse: collapse; font-size: 14px; }}
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
    <label for="invite_code">邀请码</label>
    <input id="invite_code" name="invite_code" autocomplete="off" required>
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


def audit_page(user, params):
    rows, next_cursor, message = query_audit(params)
    query = {key: params.get(key, [""])[0] for key in params}
    body = f"""
<h1>操作查询</h1>
<section class="panel">
  <form method="get" action="/audit">
    <div class="grid">
      <div>
        <label for="action">操作</label>
        <select id="action" name="action">
          {option("", "请选择", query.get("action"))}
          {option("BLOCK_PLACE", "放置方块", query.get("action"))}
          {option("BLOCK_BREAK", "破坏方块", query.get("action"))}
          {option("CONTAINER_ADD", "存入容器", query.get("action"))}
          {option("CONTAINER_REMOVE", "取出容器", query.get("action"))}
          {option("PLAYER_JOIN", "玩家进入", query.get("action"))}
          {option("PLAYER_QUIT", "玩家退出", query.get("action"))}
        </select>
      </div>
      <div>
        <label for="player">操作来源：玩家名或 UUID</label>
        <input id="player" name="player" value="{esc(query.get("player", ""))}" placeholder="例如 ExamplePlayer">
      </div>
      <div>
        <label for="target_type">作用目标：方块/容器类型</label>
        <input id="target_type" name="target_type" value="{esc(query.get("target_type", ""))}" placeholder="例如 STONE / CHEST">
      </div>
      <div>
        <label for="item_type">物品类型</label>
        <input id="item_type" name="item_type" value="{esc(query.get("item_type", ""))}" placeholder="例如 DIAMOND">
      </div>
      <div>
        <label for="world">世界</label>
        <input id="world" name="world" value="{esc(query.get("world", ""))}" placeholder="main">
      </div>
      <div>
        <label for="x">X</label>
        <input id="x" name="x" value="{esc(query.get("x", ""))}" inputmode="numeric">
      </div>
      <div>
        <label for="y">Y</label>
        <input id="y" name="y" value="{esc(query.get("y", ""))}" inputmode="numeric">
      </div>
      <div>
        <label for="z">Z</label>
        <input id="z" name="z" value="{esc(query.get("z", ""))}" inputmode="numeric">
      </div>
      <div>
        <label for="radius">半径，最大 {MAX_RADIUS}</label>
        <input id="radius" name="radius" value="{esc(query.get("radius", ""))}" inputmode="numeric" placeholder="10">
      </div>
      <div>
        <label for="limit">每页数量，最大 {MAX_QUERY_LIMIT}</label>
        <input id="limit" name="limit" value="{esc(query.get("limit", str(DEFAULT_QUERY_LIMIT)))}" inputmode="numeric">
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
"""
    return page("操作查询", body, user=user, active="audit")


def option(value, label, selected):
    attr = " selected" if value == (selected or "") else ""
    return f'<option value="{esc(value)}"{attr}>{esc(label)}</option>'


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
<table>
  <thead><tr><th>时间</th><th>操作</th><th>来源</th><th>位置</th><th>目标</th><th>物品</th><th>数量/秒数</th></tr></thead>
  <tbody>{''.join(table_rows)}</tbody>
</table>
{next_link}
"""


def query_audit(params):
    if not params:
        return [], None, "请先设置筛选条件。默认不会展示任何审计记录。"
    try:
        where, values, real_filters = build_audit_filters(params)
        if not real_filters:
            return [], None, "请至少设置一个筛选条件，避免大范围查询。"

        limit = clamp_int(first(params, "limit"), DEFAULT_QUERY_LIMIT, 1, MAX_QUERY_LIMIT)
        sql = """
            SELECT id, created_at, action, player_uuid, player_name, world, x, y, z,
                   target_type, item_type, item_amount
            FROM audit_log
            WHERE {where}
            ORDER BY created_at DESC, id DESC
            LIMIT %s
        """.format(where=" AND ".join(where))
        values.append(limit + 1)
        with db_connect() as conn, conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute(sql, values)
            rows = cur.fetchall()
        next_cursor = None
        if len(rows) > limit:
            last = rows[limit - 1]
            next_cursor = b64url(json.dumps({"t": last["created_at"], "id": last["id"]}).encode("utf-8"))
            rows = rows[:limit]
        return rows, next_cursor, ""
    except Exception as exc:
        return [], None, f"错误：{exc}"


def build_audit_filters(params):
    where = []
    values = []
    real_filters = 0

    action = first(params, "action").upper()
    allowed_actions = {"BLOCK_PLACE", "BLOCK_BREAK", "CONTAINER_ADD", "CONTAINER_REMOVE", "PLAYER_JOIN", "PLAYER_QUIT"}
    if action:
        if action not in allowed_actions:
            raise ValueError("未知操作类型")
        where.append("action = %s")
        values.append(action)
        real_filters += 1

    player = first(params, "player")
    if player:
        if UUID_RE.fullmatch(player):
            where.append("player_uuid = %s")
        else:
            where.append("player_name = %s")
        values.append(player)
        real_filters += 1

    target_type = first(params, "target_type").upper()
    if target_type:
        where.append("target_type = %s")
        values.append(target_type)
        real_filters += 1

    item_type = first(params, "item_type").upper()
    if item_type:
        where.append("item_type = %s")
        values.append(item_type)
        real_filters += 1

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
        real_filters += 1

    cursor = first(params, "cursor")
    if cursor:
        decoded = json.loads(b64url_decode(cursor).decode("utf-8"))
        where.append("(created_at < %s OR (created_at = %s AND id < %s))")
        values.extend([int(decoded["t"]), int(decoded["t"]), int(decoded["id"])])

    if not where:
        where.append("FALSE")
    return where, values, real_filters


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
            invite_code = first(params, "invite_code")
        except ValueError as exc:
            self.respond(*register_page(str(exc), HTTPStatus.BAD_REQUEST))
            return

        if not USERNAME_RE.fullmatch(username):
            self.respond(*register_page("Minecraft ID 格式不正确。", HTTPStatus.BAD_REQUEST))
            return
        if not hmac.compare_digest(invite_code, INVITE_CODE):
            self.respond(*register_page("邀请码不正确。", HTTPStatus.FORBIDDEN))
            return

        try:
            result = RconClient(RCON_HOST, RCON_PORT, RCON_PASSWORD).run(f"whitelist add {username}")
        except Exception as exc:
            self.respond(*register_page(f"白名单服务暂时不可用：{exc}", HTTPStatus.INTERNAL_SERVER_ERROR))
            return

        body = f"""
<section class="login-card">
  <h1>已提交白名单</h1>
  <p>玩家 <strong>{esc(username)}</strong> 已提交加入白名单。</p>
  <p>{esc(result or "已提交白名单命令。")}</p>
  <div class="actions"><a class="button" href="/">返回登录</a></div>
</section>
"""
        self.respond(*page("已提交白名单", body))

    def send_redirect(self, location, cookie=None):
        headers = [("Location", location)]
        if cookie:
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
