import socket
import struct


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
