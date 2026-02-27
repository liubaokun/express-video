import os
import socket
import threading
from datetime import datetime
from pathlib import Path
from typing import Callable, Optional

from flask import Flask, request, jsonify
from werkzeug.utils import secure_filename


def is_port_in_use(port: int) -> bool:
    """检查端口是否被占用"""
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            result = s.connect_ex(('127.0.0.1', port))
            return result == 0
        except:
            return False


class HttpServer:
    def __init__(
        self,
        save_path: str,
        port: int = 8080,
        on_file_received: Optional[Callable[[str, str, str], None]] = None,
        on_error: Optional[Callable[[str], None]] = None
    ):
        self.save_path = Path(save_path)
        self.port = port
        self.on_file_received = on_file_received
        self.on_error = on_error

        self.app = Flask(__name__)
        self.server_thread: Optional[threading.Thread] = None
        self.is_running = False
        self._socket = None

        self._setup_routes()
        self._ensure_save_path()

    def _ensure_save_path(self):
        self.save_path.mkdir(parents=True, exist_ok=True)

    def _setup_routes(self):
        @self.app.before_request
        def log_request():
            print(f"[{datetime.now().strftime('%H:%M:%S')}] {request.method} {request.path}")

        @self.app.route('/ping', methods=['GET'])
        def ping():
            return jsonify({"status": "ok", "message": "Server is running"})

        @self.app.route('/upload', methods=['POST'])
        def upload_file():
            print(f"[{datetime.now().strftime('%H:%M:%S')}] 收到上传请求")
            try:
                if 'file' not in request.files:
                    print(f"[{datetime.now().strftime('%H:%M:%S')}] 错误：未提供文件")
                    return jsonify({"error": "No file provided"}), 400

                file = request.files['file']
                tracking_number = request.form.get('trackingNumber', 'unknown')
                print(f"[{datetime.now().strftime('%H:%M:%S')}] 快递单号：{tracking_number}, 文件名：{file.filename}")

                if file.filename == '':
                    return jsonify({"error": "No file selected"}), 400

                self._ensure_save_path()

                timestamp = datetime.now().strftime("%H时%M分%S秒")
                filename = f"{secure_filename(tracking_number)}_{timestamp}.mp4"
                if not filename.endswith('.mp4'):
                    filename += '.mp4'

                filepath = self.save_path / filename

                print(f"[{datetime.now().strftime('%H:%M:%S')}] 保存文件：{filepath}")
                file.save(str(filepath))
                print(f"[{datetime.now().strftime('%H:%M:%S')}] 保存成功")

                if self.on_file_received:
                    self.on_file_received(
                        tracking_number,
                        str(filepath),
                        f"{os.path.getsize(filepath) / (1024*1024):.2f} MB"
                    )

                return jsonify({
                    "status": "success",
                    "message": f"File saved: {filename}",
                    "path": str(filepath)
                })

            except Exception as e:
                error_msg = f"Upload error: {str(e)}"
                print(f"[{datetime.now().strftime('%H:%M:%S')}] {error_msg}")
                import traceback
                traceback.print_exc()
                if self.on_error:
                    self.on_error(error_msg)
                return jsonify({"error": error_msg}), 500

        @self.app.route('/status', methods=['GET'])
        def status():
            return jsonify({
                "status": "running",
                "save_path": str(self.save_path),
                "port": self.port
            })

    def set_save_path(self, path: str):
        self.save_path = Path(path)
        self._ensure_save_path()

    def start(self):
        if self.is_running:
            raise Exception("服务器已经在运行")
        
        if is_port_in_use(self.port):
            raise Exception(f"端口 {self.port} 已被占用，请关闭其他程序或更换端口")

        self.is_running = True
        try:
            self.server_thread = threading.Thread(
                target=lambda: self.app.run(
                    host='0.0.0.0',
                    port=self.port,
                    threaded=True,
                    use_reloader=False
                ),
                daemon=True
            )
            self.server_thread.start()
        except Exception as e:
            self.is_running = False
            raise e

    def stop(self):
        self.is_running = False
