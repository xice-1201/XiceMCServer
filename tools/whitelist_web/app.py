#!/usr/bin/env python3
import hmac
import html
import os
import re
import socket
import struct
import time
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs


USERNAME_RE = re.compile(r"^[A-Za-z0-9_]{3,16}$")
RATE_LIMIT_WINDOW_SECONDS = 60
RATE_LIMIT_MAX_ATTEMPTS = 10
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


def page(title, body, status=HTTPStatus.OK):
    escaped_title = html.escape(title)
    return status, f"""<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>{escaped_title}</title>
  <style>
    body {{
      margin: 0;
      min-height: 100vh;
      display: grid;
      place-items: center;
      font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      background: #101418;
      color: #edf2f7;
    }}
    main {{
      width: min(92vw, 420px);
      padding: 28px;
      border: 1px solid #28323d;
      border-radius: 8px;
      background: #171d24;
    }}
    h1 {{ margin: 0 0 18px; font-size: 24px; }}
    label {{ display: block; margin: 14px 0 6px; color: #cbd5e1; }}
    input {{
      width: 100%;
      box-sizing: border-box;
      padding: 11px 12px;
      border-radius: 6px;
      border: 1px solid #3b4654;
      background: #0f141a;
      color: #f8fafc;
      font-size: 16px;
    }}
    button {{
      width: 100%;
      margin-top: 18px;
      padding: 11px 12px;
      border: 0;
      border-radius: 6px;
      background: #2f8cff;
      color: white;
      font-size: 16px;
      cursor: pointer;
    }}
    p {{ line-height: 1.6; color: #cbd5e1; }}
    .message {{ margin-top: 0; }}
    a {{ color: #8cc8ff; }}
  </style>
</head>
<body>
  <main>
    {body}
  </main>
</body>
</html>"""


def form(message=""):
    safe_message = f'<p class="message">{html.escape(message)}</p>' if message else ""
    return page(
        "XiceMCServer 白名单注册",
        f"""
<h1>XiceMCServer 白名单注册</h1>
{safe_message}
<form method="post" action="/">
  <label for="username">Minecraft Java 版 ID</label>
  <input id="username" name="username" autocomplete="username" required minlength="3" maxlength="16" pattern="[A-Za-z0-9_]+">
  <label for="invite_code">邀请码</label>
  <input id="invite_code" name="invite_code" autocomplete="off" required>
  <button type="submit">加入白名单</button>
</form>
""",
    )


class Handler(BaseHTTPRequestHandler):
    server_version = "XiceWhitelist/0.1"

    def log_message(self, fmt, *args):
        print(f"{self.address_string()} - {fmt % args}")

    def do_GET(self):
        if self.path != "/":
            self.respond(*page("未找到", "<h1>未找到</h1>", HTTPStatus.NOT_FOUND))
            return
        self.respond(*form())

    def do_POST(self):
        if self.path != "/":
            self.respond(*page("未找到", "<h1>未找到</h1>", HTTPStatus.NOT_FOUND))
            return

        ip = self.client_address[0]
        if rate_limited(ip):
            self.respond(*form("请求过于频繁，请稍后再试。"), status=HTTPStatus.TOO_MANY_REQUESTS)
            return

        length = int(self.headers.get("Content-Length", "0"))
        if length > 2048:
            self.respond(*form("请求内容过大。"), status=HTTPStatus.BAD_REQUEST)
            return

        data = self.rfile.read(length).decode("utf-8", "replace")
        params = parse_qs(data)
        username = params.get("username", [""])[0].strip()
        invite_code = params.get("invite_code", [""])[0].strip()

        if not USERNAME_RE.fullmatch(username):
            self.respond(*form("Minecraft ID 格式不正确。"), status=HTTPStatus.BAD_REQUEST)
            return

        if not hmac.compare_digest(invite_code, INVITE_CODE):
            self.respond(*form("邀请码不正确。"), status=HTTPStatus.FORBIDDEN)
            return

        try:
            result = RconClient(RCON_HOST, RCON_PORT, RCON_PASSWORD).run(f"whitelist add {username}")
        except Exception as exc:
            self.respond(*form(f"白名单服务暂时不可用：{exc}"), status=HTTPStatus.INTERNAL_SERVER_ERROR)
            return

        safe_user = html.escape(username)
        safe_result = html.escape(result or "已提交白名单命令。")
        self.respond(
            *page(
                "已加入白名单",
                f"""
<h1>已加入白名单</h1>
<p>玩家 <strong>{safe_user}</strong> 已提交加入白名单。</p>
<p>{safe_result}</p>
<p><a href="/">继续添加</a></p>
""",
            )
        )

    def respond(self, status, content, **override):
        status = override.get("status", status)
        encoded = content.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(encoded)


def main():
    httpd = ThreadingHTTPServer((HOST, PORT), Handler)
    print(f"Whitelist web listening on {HOST}:{PORT}")
    httpd.serve_forever()


if __name__ == "__main__":
    main()
