#!/usr/bin/env python3
"""
mock-dsp —— DSP 免密（Passwordless）本地桩服务。

仅用于本地端到端验证，模拟 HSBC DSP 的两个动作：
  1) GET  /authenticate           —— 浏览器侧"取 AMToken"占位（返回一个假的 AMToken）。
  2) POST /translator             —— 后端用 AMToken 换 E2E/JWT；返回 {"issued_token": <JWT>}。

说明（与生产差异）：
  - 真实 DSP 的 translator 会做签名 + manifest 公钥校验；本桩返回 alg=none 的 **未签名** JWT，
    与后端当前 DspJwtDecoder「仅 Base64URL 解 payload、不验签」的实现一致（这是已登记的安全整改点）。
  - issued_token 的 payload 固定指向种子 LDAP 用户（employeeID=100001 / uid=alice），
    便于验证 DspSsoService → LDAP JIT → 签发 SSO code 全链路。
"""
import base64
import json
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = int(os.environ.get("MOCK_DSP_PORT", "9099"))

# 桩身份：与 deploy/environments/dev/ldap/seed.ldif 中的种子用户 alice 对齐
CLAIM_EMPLOYEE_ID = os.environ.get("MOCK_DSP_EMPLOYEE_ID", "100001")
CLAIM_UID = os.environ.get("MOCK_DSP_UID", "alice")


def _b64url(raw: bytes) -> str:
    """Base64URL 编码（去除 padding，与 JWT 规范一致）。"""
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode("ascii")


def build_unsigned_jwt() -> str:
    """构造一个 alg=none 的未签名 JWT：header.payload.（空签名）。"""
    header = {"alg": "none", "typ": "JWT"}
    payload = {
        "employeeID": CLAIM_EMPLOYEE_ID,
        "uid": CLAIM_UID,
        "preferred_username": CLAIM_UID,
        "sub": CLAIM_UID,
        "iss": "mock-dsp",
    }
    segments = [
        _b64url(json.dumps(header, separators=(",", ":")).encode("utf-8")),
        _b64url(json.dumps(payload, separators=(",", ":")).encode("utf-8")),
        "",  # 未签名：第三段为空
    ]
    return ".".join(segments)


class Handler(BaseHTTPRequestHandler):
    def _send_json(self, code: int, body: dict) -> None:
        data = json.dumps(body).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):  # noqa: N802 (BaseHTTPRequestHandler 命名约定)
        if self.path.startswith("/authenticate"):
            # 浏览器侧"取 AMToken"占位
            self._send_json(200, {"AMToken": "mock-am-token-for-" + CLAIM_UID})
            return
        if self.path.startswith("/health"):
            self._send_json(200, {"status": "UP"})
            return
        self._send_json(404, {"error": "not_found", "path": self.path})

    def do_POST(self):  # noqa: N802
        if self.path.startswith("/translator"):
            length = int(self.headers.get("Content-Length", "0") or "0")
            _ = self.rfile.read(length) if length else b""  # 读取并忽略 AMToken 请求体
            self._send_json(200, {"issued_token": build_unsigned_jwt()})
            return
        self._send_json(404, {"error": "not_found", "path": self.path})

    def log_message(self, fmt, *args):  # 静默默认逐请求日志，保持容器日志整洁
        return


if __name__ == "__main__":
    server = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    print(f"[mock-dsp] listening on :{PORT} (employeeID={CLAIM_EMPLOYEE_ID}, uid={CLAIM_UID})", flush=True)
    server.serve_forever()
