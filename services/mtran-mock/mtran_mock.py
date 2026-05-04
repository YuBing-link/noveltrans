"""MTranServer Mock - 轻量级模拟 MTranServer 翻译接口"""
import json
import logging
import time
from http.server import HTTPServer, BaseHTTPRequestHandler

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("mtran-mock")

LANGUAGE_NAMES = {
    "zh": "简体中文", "en": "English", "ja": "日本語", "ko": "한국어",
    "fr": "français", "de": "Deutsch", "es": "español", "ru": "русский",
}

class MockHandler(BaseHTTPRequestHandler):
    request_count = 0

    def do_POST(self):
        if self.path == "/translate":
            length = int(self.headers.get("Content-Length", 0))
            body = json.loads(self.rfile.read(length)) if length else {}
            text = body.get("text", "")
            target = body.get("to", "zh")
            lang = LANGUAGE_NAMES.get(target, target)

            # 模拟翻译：在每行前加语言标签
            if body.get("html", False):
                result = f"[{lang}] {text}"
            else:
                lines = text.split("\n")
                result = "\n".join(f"[{lang}] {l}" if l.strip() else l for l in lines)

            MockHandler.request_count += 1
            response = json.dumps({"result": result})

            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(response.encode())
            self.log_translate(text[:50])
        else:
            self.send_response(404)
            self.end_headers()

    def do_GET(self):
        if self.path == "/health":
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"status": "UP", "requests_handled": MockHandler.request_count}).encode())
        else:
            self.send_response(404)
            self.end_headers()

    def log_translate(self, text_preview):
        logger.info(f"POST /translate -> 200 (handled={MockHandler.request_count}) [{text_preview[:50]}...]")

    def log_request(self, code="-", size="-"):
        pass  # suppress default stderr logging (called by send_response)

    def log_message(self, format, *args):
        pass  # suppress default stderr logging

if __name__ == "__main__":
    server = HTTPServer(("0.0.0.0", 8989), MockHandler)
    logger.info("MTranServer Mock 启动: http://0.0.0.0:8989")
    server.serve_forever()
