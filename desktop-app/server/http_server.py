import os
import threading
from datetime import datetime
from pathlib import Path
from typing import Callable, Optional

from flask import Flask, request, jsonify
from werkzeug.utils import secure_filename


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

        self._setup_routes()
        self._ensure_save_path()

    def _ensure_save_path(self):
        self.save_path.mkdir(parents=True, exist_ok=True)

    def _setup_routes(self):
        @self.app.route('/ping', methods=['GET'])
        def ping():
            return jsonify({"status": "ok", "message": "Server is running"})

        @self.app.route('/upload', methods=['POST'])
        def upload_file():
            try:
                if 'file' not in request.files:
                    return jsonify({"error": "No file provided"}), 400

                file = request.files['file']
                tracking_number = request.form.get('trackingNumber', 'unknown')

                if file.filename == '':
                    return jsonify({"error": "No file selected"}), 400

                self._ensure_save_path()

                timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
                filename = f"{secure_filename(tracking_number)}_{timestamp}.mp4"
                if not filename.endswith('.mp4'):
                    filename += '.mp4'

                filepath = self.save_path / filename

                file.save(str(filepath))

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
            return

        self.is_running = True
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

    def stop(self):
        self.is_running = False
