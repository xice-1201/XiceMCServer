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
import secrets as token_secrets
from datetime import datetime
from http import HTTPStatus
from http.cookies import SimpleCookie
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib import request as urlrequest
from urllib.error import HTTPError, URLError
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
MICROSOFT_AUTH_ENABLED = env_bool("MICROSOFT_AUTH_ENABLED", False)
MICROSOFT_CLIENT_ID = env("MICROSOFT_CLIENT_ID", "")
MICROSOFT_CLIENT_SECRET = env("MICROSOFT_CLIENT_SECRET", "")
MICROSOFT_REDIRECT_URI = env("MICROSOFT_REDIRECT_URI", "")
MICROSOFT_SCOPE = env("MICROSOFT_SCOPE", "XboxLive.signin offline_access")
MICROSOFT_AUTH_URL = env("MICROSOFT_AUTH_URL", "https://login.live.com/oauth20_authorize.srf")
MICROSOFT_TOKEN_URL = env("MICROSOFT_TOKEN_URL", "https://login.live.com/oauth20_token.srf")
MICROSOFT_REGISTER_INVITE_REQUIRED = env_bool("MICROSOFT_REGISTER_INVITE_REQUIRED", True)
OAUTH_STATE_SECONDS = 10 * 60


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


def microsoft_auth_available():
    return bool(MICROSOFT_AUTH_ENABLED and MICROSOFT_CLIENT_ID and MICROSOFT_REDIRECT_URI)


def make_oauth_state(mode):
    verifier = token_secrets.token_urlsafe(48)
    state = token_secrets.token_urlsafe(24)
    payload = {
        "state": state,
        "mode": mode,
        "verifier": verifier,
        "exp": int(time.time()) + OAUTH_STATE_SECONDS,
    }
    body = b64url(json.dumps(payload, separators=(",", ":")).encode("utf-8"))
    return payload, f"{body}.{sign(body)}"


def parse_oauth_state(cookie_header):
    if not cookie_header:
        return None
    cookie = SimpleCookie()
    cookie.load(cookie_header)
    morsel = cookie.get("xicemc_oauth")
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
    return payload


def microsoft_authorize_url(payload):
    challenge = b64url(hashlib.sha256(payload["verifier"].encode("ascii")).digest())
    params = {
        "client_id": MICROSOFT_CLIENT_ID,
        "response_type": "code",
        "redirect_uri": MICROSOFT_REDIRECT_URI,
        "scope": MICROSOFT_SCOPE,
        "state": payload["state"],
        "code_challenge": challenge,
        "code_challenge_method": "S256",
        "prompt": "select_account",
    }
    return f"{MICROSOFT_AUTH_URL}?{urlencode(params)}"


def post_form(url, data):
    encoded = urlencode(data).encode("utf-8")
    req = urlrequest.Request(
        url,
        data=encoded,
        headers={"Content-Type": "application/x-www-form-urlencoded", "Accept": "application/json"},
        method="POST",
    )
    return read_json_response(req)


def post_json(url, data):
    encoded = json.dumps(data).encode("utf-8")
    req = urlrequest.Request(
        url,
        data=encoded,
        headers={"Content-Type": "application/json", "Accept": "application/json"},
        method="POST",
    )
    return read_json_response(req)


def get_json(url, bearer_token):
    req = urlrequest.Request(url, headers={"Authorization": f"Bearer {bearer_token}", "Accept": "application/json"})
    return read_json_response(req)


def read_json_response(req):
    try:
        with urlrequest.urlopen(req, timeout=12) as response:
            return json.loads(response.read().decode("utf-8"))
    except HTTPError as exc:
        detail = exc.read().decode("utf-8", "replace")
        raise RuntimeError(f"HTTP {exc.code}: {detail}") from exc
    except URLError as exc:
        raise RuntimeError(f"无法连接身份验证服务：{exc.reason}") from exc


def exchange_microsoft_code(code, verifier):
    data = {
        "client_id": MICROSOFT_CLIENT_ID,
        "code": code,
        "grant_type": "authorization_code",
        "redirect_uri": MICROSOFT_REDIRECT_URI,
        "code_verifier": verifier,
    }
    if MICROSOFT_CLIENT_SECRET:
        data["client_secret"] = MICROSOFT_CLIENT_SECRET
    token = post_form(MICROSOFT_TOKEN_URL, data)
    access_token = token.get("access_token")
    if not access_token:
        raise RuntimeError("Microsoft 未返回 access_token")
    return access_token


def load_minecraft_profile(microsoft_access_token):
    xbox = post_json(
        "https://user.auth.xboxlive.com/user/authenticate",
        {
            "Properties": {
                "AuthMethod": "RPS",
                "SiteName": "user.auth.xboxlive.com",
                "RpsTicket": f"d={microsoft_access_token}",
            },
            "RelyingParty": "http://auth.xboxlive.com",
            "TokenType": "JWT",
        },
    )
    xbox_token = xbox.get("Token")
    user_hash = xbox.get("DisplayClaims", {}).get("xui", [{}])[0].get("uhs")
    if not xbox_token or not user_hash:
        raise RuntimeError("Xbox Live 未返回有效登录令牌")

    xsts = post_json(
        "https://xsts.auth.xboxlive.com/xsts/authorize",
        {
            "Properties": {"SandboxId": "RETAIL", "UserTokens": [xbox_token]},
            "RelyingParty": "rp://api.minecraftservices.com/",
            "TokenType": "JWT",
        },
    )
    xsts_token = xsts.get("Token")
    if not xsts_token:
        raise RuntimeError("XSTS 未返回有效授权令牌")

    minecraft = post_json(
        "https://api.minecraftservices.com/authentication/login_with_xbox",
        {"identityToken": f"XBL3.0 x={user_hash};{xsts_token}"},
    )
    minecraft_token = minecraft.get("access_token")
    if not minecraft_token:
        raise RuntimeError("Minecraft 服务未返回 access_token")

    profile = get_json("https://api.minecraftservices.com/minecraft/profile", minecraft_token)
    if not profile.get("id") or not profile.get("name"):
        raise RuntimeError("该 Microsoft 账号未找到 Minecraft Java Profile")
    return {"uuid": canonical_uuid(profile["id"]), "name": profile["name"]}


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
    microsoft_login = ""
    if microsoft_auth_available():
        microsoft_login = """
  <div class="divider"></div>
  <div class="actions">
    <a class="button" href="/auth/microsoft/start?mode=login">使用 Microsoft 登录</a>
  </div>
"""
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
  {microsoft_login}
</section>
"""
    return page("XiceMCServer 登录", body, status)


def register_page(message="", status=HTTPStatus.OK):
    safe_message = f'<p class="message">{esc(message)}</p>' if message else ""
    microsoft_register = ""
    if microsoft_auth_available():
        invite_field = ""
        if MICROSOFT_REGISTER_INVITE_REQUIRED:
            invite_field = """
    <label for="microsoft_invite_code">邀请码</label>
    <input id="microsoft_invite_code" name="invite_code" autocomplete="off" required>
"""
        microsoft_register = f"""
  <div class="divider"></div>
  <form method="get" action="/auth/microsoft/start">
    <input type="hidden" name="mode" value="register">
    {invite_field}
    <div class="actions">
      <button type="submit">使用 Microsoft 注册白名单</button>
    </div>
  </form>
"""
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
  {microsoft_register}
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
    container_options_html = container_options(query.get("target_type", ""))
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
        <input id="time_from" name="time_from" type="datetime-local" value="{esc(query.get("time_from", ""))}">
      </div>
      <div>
        <label for="time_to">结束时间</label>
        <input id="time_to" name="time_to" type="datetime-local" value="{esc(query.get("time_to", ""))}">
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
        where.append("created_at >= %s")
        values.append(time_from)

    time_to = parse_datetime_local(first(params, "time_to"))
    if time_to is not None:
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
        if parsed.path == "/auth/microsoft/start":
            self.handle_microsoft_start(parse_qs(parsed.query))
            return
        if parsed.path == "/auth/microsoft/callback":
            self.handle_microsoft_callback(parse_qs(parsed.query))
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

    def handle_microsoft_start(self, params):
        if not microsoft_auth_available():
            self.respond(*login_page("Microsoft 身份验证尚未配置。", HTTPStatus.SERVICE_UNAVAILABLE))
            return

        mode = first(params, "mode") or "login"
        if mode not in {"login", "register"}:
            self.respond(*login_page("Microsoft 身份验证请求无效。", HTTPStatus.BAD_REQUEST))
            return

        if mode == "register" and MICROSOFT_REGISTER_INVITE_REQUIRED:
            invite_code = first(params, "invite_code")
            if not hmac.compare_digest(invite_code, INVITE_CODE):
                self.respond(*register_page("邀请码不正确。", HTTPStatus.FORBIDDEN))
                return

        payload, cookie_value = make_oauth_state(mode)
        cookie = f"xicemc_oauth={cookie_value}; Path=/; Max-Age={OAUTH_STATE_SECONDS}; HttpOnly; SameSite=Lax"
        self.send_redirect(microsoft_authorize_url(payload), cookie)

    def handle_microsoft_callback(self, params):
        state_payload = parse_oauth_state(self.headers.get("Cookie"))
        clear_oauth_cookie = "xicemc_oauth=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax"
        if not state_payload:
            self.respond(*login_page("Microsoft 登录状态已过期，请重新开始。", HTTPStatus.BAD_REQUEST))
            return

        mode = state_payload.get("mode", "login")
        if first(params, "error"):
            message = first(params, "error_description") or first(params, "error")
            page_func = register_page if mode == "register" else login_page
            self.respond(*page_func(f"Microsoft 身份验证失败：{message}", HTTPStatus.BAD_REQUEST), extra_headers=[("Set-Cookie", clear_oauth_cookie)])
            return
        if first(params, "state") != state_payload.get("state"):
            self.respond(*login_page("Microsoft 登录状态校验失败。", HTTPStatus.BAD_REQUEST), extra_headers=[("Set-Cookie", clear_oauth_cookie)])
            return

        code = first(params, "code")
        if not code:
            self.respond(*login_page("Microsoft 未返回授权码。", HTTPStatus.BAD_REQUEST), extra_headers=[("Set-Cookie", clear_oauth_cookie)])
            return

        try:
            microsoft_token = exchange_microsoft_code(code, state_payload["verifier"])
            profile = load_minecraft_profile(microsoft_token)
            entry = whitelist_entry_by_uuid(profile["uuid"]) or whitelist_entry(profile["name"])

            if mode == "register":
                if not entry:
                    RconClient(RCON_HOST, RCON_PORT, RCON_PASSWORD).run(f"whitelist add {profile['name']}")
                    entry = whitelist_entry_by_uuid(profile["uuid"]) or whitelist_entry(profile["name"]) or profile
                ensure_web_player(entry)
                session_cookie = f"xicemc_session={make_session(entry)}; Path=/; Max-Age={SESSION_SECONDS}; HttpOnly; SameSite=Lax"
                self.send_redirect("/home", [session_cookie, clear_oauth_cookie])
                return

            if not entry:
                self.respond(
                    *login_page("该 Microsoft 账号对应的 Minecraft Java 玩家不在白名单内，请先注册白名单。", HTTPStatus.FORBIDDEN),
                    extra_headers=[("Set-Cookie", clear_oauth_cookie)],
                )
                return
            ensure_web_player(entry)
            session_cookie = f"xicemc_session={make_session(entry)}; Path=/; Max-Age={SESSION_SECONDS}; HttpOnly; SameSite=Lax"
            self.send_redirect("/home", [session_cookie, clear_oauth_cookie])
        except Exception as exc:
            page_func = register_page if mode == "register" else login_page
            self.respond(*page_func(f"Microsoft 身份验证失败：{exc}", HTTPStatus.BAD_GATEWAY), extra_headers=[("Set-Cookie", clear_oauth_cookie)])

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
            entry = whitelist_entry(username)
            if entry:
                ensure_web_player(entry)
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
