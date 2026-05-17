#!/usr/bin/env python3
import os
import socket
import struct
import sys


class RconClient:
    AUTH = 3
    COMMAND = 2

    def __init__(self, host, port, password, timeout=8):
        self.host = host
        self.port = port
        self.password = password
        self.timeout = timeout
        self.request_id = 100

    def packet(self, packet_type, payload):
        body = struct.pack("<ii", self.request_id, packet_type)
        body += payload.encode("utf-8")
        body += b"\x00\x00"
        return struct.pack("<i", len(body)) + body

    def read_exact(self, sock, size):
        chunks = []
        remaining = size
        while remaining:
            chunk = sock.recv(remaining)
            if not chunk:
                raise ConnectionError("RCON connection closed unexpectedly")
            chunks.append(chunk)
            remaining -= len(chunk)
        return b"".join(chunks)

    def read_packet(self, sock):
        raw_length = self.read_exact(sock, 4)
        length = struct.unpack("<i", raw_length)[0]
        data = self.read_exact(sock, length)
        request_id, packet_type = struct.unpack("<ii", data[:8])
        payload = data[8:-2].decode("utf-8", "replace")
        return request_id, packet_type, payload

    def run(self, command):
        with socket.create_connection((self.host, self.port), timeout=self.timeout) as sock:
            sock.settimeout(self.timeout)
            sock.sendall(self.packet(self.AUTH, self.password))
            request_id, _, _ = self.read_packet(sock)
            if request_id == -1:
                raise PermissionError("RCON authentication failed")

            self.request_id += 1
            sock.sendall(self.packet(self.COMMAND, command))
            _, _, payload = self.read_packet(sock)
            return payload.strip()


def main():
    if len(sys.argv) < 2:
        print("Usage: rcon-command.py <command>", file=sys.stderr)
        return 2
    password = os.environ.get("XICEMC_RCON_PASSWORD")
    if not password:
        print("Missing XICEMC_RCON_PASSWORD", file=sys.stderr)
        return 2
    host = os.environ.get("XICEMC_RCON_HOST", "127.0.0.1")
    port = int(os.environ.get("XICEMC_RCON_PORT", "25575"))
    command = " ".join(sys.argv[1:])
    result = RconClient(host, port, password).run(command)
    if result:
        print(result)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
