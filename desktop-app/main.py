import socket
import sys
from pathlib import Path
from typing import Optional
from datetime import datetime

from PyQt5.QtCore import Qt, QThread, pyqtSignal, QTimer
from PyQt5.QtWidgets import (
    QApplication,
    QMainWindow,
    QWidget,
    QVBoxLayout,
    QHBoxLayout,
    QLabel,
    QPushButton,
    QLineEdit,
    QSpinBox,
    QFileDialog,
    QGroupBox,
    QTextEdit,
    QSystemTrayIcon,
    QMenu,
    QAction,
    QDialog,
    QFrame
)
from PyQt5.QtGui import QIcon, QFont, QPixmap, QImage
from PyQt5.QtCore import Qt as QtCoreQt

from config.config_manager import ConfigManager
from server.http_server import HttpServer

import qrcode
from io import BytesIO


class SuccessDialog(QDialog):
    def __init__(self, filename: str, size: str, parent=None):
        super().__init__(parent)
        self.setWindowTitle("Video Received")
        self.setFixedSize(300, 150)
        self.setWindowFlags(self.windowFlags() | QtCoreQt.WindowStaysOnTopHint)
        
        layout = QVBoxLayout(self)
        layout.setSpacing(10)
        
        title_label = QLabel("Video Received Successfully")
        title_label.setFont(QFont("Arial", 14, QFont.Bold))
        title_label.setStyleSheet("color: #4CAF50;")
        title_label.setAlignment(QtCoreQt.AlignCenter)
        layout.addWidget(title_label)
        
        file_label = QLabel(f"File: {filename}")
        file_label.setAlignment(QtCoreQt.AlignCenter)
        layout.addWidget(file_label)
        
        size_label = QLabel(f"Size: {size}")
        size_label.setAlignment(QtCoreQt.AlignCenter)
        layout.addWidget(size_label)
        
        self.timer = QTimer()
        self.timer.timeout.connect(self.close)
        self.timer.start(2000)


class ServerThread(QThread):
    started_signal = pyqtSignal()
    stopped_signal = pyqtSignal()
    error_signal = pyqtSignal(str)

    def __init__(self, server: HttpServer):
        super().__init__()
        self.server = server

    def run(self):
        try:
            self.server.start()
            self.started_signal.emit()
        except Exception as e:
            self.error_signal.emit(str(e))


class MainWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.config_manager = ConfigManager()
        self.server: Optional[HttpServer] = None
        self.server_thread: Optional[ServerThread] = None

        self._init_ui()
        self._init_tray()
        self._load_config()

    def _init_ui(self):
        self.setWindowTitle("Express Video Receiver")
        self.setMinimumSize(550, 600)

        central_widget = QWidget()
        self.setCentralWidget(central_widget)

        layout = QVBoxLayout(central_widget)
        layout.setSpacing(12)
        layout.setContentsMargins(20, 20, 20, 20)

        status_group = QGroupBox("Service Status")
        status_layout = QVBoxLayout(status_group)

        self.status_label = QLabel("Stopped")
        self.status_label.setFont(QFont("Arial", 14))
        self.status_label.setStyleSheet("color: #666;")
        status_layout.addWidget(self.status_label)

        self.ip_label = QLabel(f"IP: {self._get_local_ip()}")
        status_layout.addWidget(self.ip_label)

        self.port_label = QLabel(f"Port: {self.config_manager.port}")
        status_layout.addWidget(self.port_label)

        layout.addWidget(status_group)

        qr_group = QGroupBox("QR Code (Scan to configure)")
        qr_layout = QVBoxLayout(qr_group)
        qr_layout.setAlignment(QtCoreQt.AlignCenter)

        self.qr_label = QLabel()
        self.qr_label.setFixedSize(200, 200)
        self.qr_label.setAlignment(QtCoreQt.AlignCenter)
        self.qr_label.setStyleSheet("background-color: white; border: 1px solid #ccc;")
        self.qr_label.setText("Start service to show QR")
        qr_layout.addWidget(self.qr_label)

        self.qr_hint = QLabel("Scan with phone app to configure server")
        self.qr_hint.setStyleSheet("color: #666; font-size: 11px;")
        qr_layout.addWidget(self.qr_hint)

        layout.addWidget(qr_group)

        settings_group = QGroupBox("Settings")
        settings_layout = QVBoxLayout(settings_group)

        path_layout = QHBoxLayout()
        path_layout.addWidget(QLabel("Save Path:"))

        self.path_edit = QLineEdit()
        self.path_edit.setReadOnly(True)
        path_layout.addWidget(self.path_edit)

        self.browse_btn = QPushButton("Browse...")
        self.browse_btn.clicked.connect(self._browse_folder)
        path_layout.addWidget(self.browse_btn)

        settings_layout.addLayout(path_layout)

        port_layout = QHBoxLayout()
        port_layout.addWidget(QLabel("Port:"))

        self.port_spin = QSpinBox()
        self.port_spin.setRange(1, 65535)
        self.port_spin.setValue(self.config_manager.port)
        port_layout.addWidget(self.port_spin)

        port_layout.addStretch()
        settings_layout.addLayout(port_layout)

        layout.addWidget(settings_group)

        control_layout = QHBoxLayout()
        self.start_btn = QPushButton("Start Service")
        self.start_btn.clicked.connect(self._toggle_server)
        self.start_btn.setMinimumHeight(40)
        control_layout.addWidget(self.start_btn)

        self.apply_btn = QPushButton("Apply Settings")
        self.apply_btn.clicked.connect(self._apply_settings)
        self.apply_btn.setMinimumHeight(40)
        control_layout.addWidget(self.apply_btn)

        layout.addLayout(control_layout)

        log_group = QGroupBox("Received Videos")
        log_layout = QVBoxLayout(log_group)

        self.log_text = QTextEdit()
        self.log_text.setReadOnly(True)
        self.log_text.setMaximumHeight(120)
        log_layout.addWidget(self.log_text)

        layout.addWidget(log_group)

    def _generate_qr_code(self, data: str):
        try:
            qr = qrcode.QRCode(
                version=1,
                error_correction=qrcode.constants.ERROR_CORRECT_L,
                box_size=10,
                border=2,
            )
            qr.add_data(data)
            qr.make(fit=True)
            
            img = qr.make_image(fill_color="black", back_color="white")
            
            buffer = BytesIO()
            img.save(buffer, format="PNG")
            buffer.seek(0)
            
            qimage = QImage()
            qimage.loadFromData(buffer.getvalue())
            
            pixmap = QPixmap.fromImage(qimage)
            scaled_pixmap = pixmap.scaled(190, 190, QtCoreQt.KeepAspectRatio, QtCoreQt.SmoothTransformation)
            
            self.qr_label.setPixmap(scaled_pixmap)
        except Exception as e:
            self.qr_label.setText(f"QR Error: {str(e)}")

    def _init_tray(self):
        self.tray_icon = QSystemTrayIcon(self)
        self.tray_icon.setToolTip("Express Video Receiver")

        tray_menu = QMenu()

        show_action = QAction("Show Window", self)
        show_action.triggered.connect(self.show)
        tray_menu.addAction(show_action)

        quit_action = QAction("Exit", self)
        quit_action.triggered.connect(self._quit_app)
        tray_menu.addAction(quit_action)

        self.tray_icon.setContextMenu(tray_menu)
        self.tray_icon.activated.connect(self._on_tray_activated)
        self.tray_icon.show()

    def _load_config(self):
        self.path_edit.setText(self.config_manager.save_path)
        self.port_spin.setValue(self.config_manager.port)
        self.port_label.setText(f"Port: {self.config_manager.port}")

        Path(self.config_manager.save_path).mkdir(parents=True, exist_ok=True)

        if self.config_manager.auto_start:
            self._start_server()

    def _browse_folder(self):
        folder = QFileDialog.getExistingDirectory(
            self,
            "Select Save Path",
            self.path_edit.text()
        )
        if folder:
            self.path_edit.setText(folder)

    def _apply_settings(self):
        self.config_manager.save_path = self.path_edit.text()
        self.config_manager.port = self.port_spin.value()
        self.port_label.setText(f"Port: {self.port_spin.value()}")

        if self.server and self.server.is_running:
            self._log("Settings saved. Restart service to apply.")
        else:
            self._log("Settings saved.")

    def _toggle_server(self):
        if self.server and self.server.is_running:
            self._stop_server()
        else:
            self._start_server()

    def _start_server(self):
        try:
            self.server = HttpServer(
                save_path=self.config_manager.save_path,
                port=self.config_manager.port,
                on_file_received=self._on_file_received,
                on_error=self._on_error
            )

            self.server.start()

            self.status_label.setText("Running")
            self.status_label.setStyleSheet("color: #4CAF50; font-weight: bold;")
            self.start_btn.setText("Stop Service")

            local_ip = self._get_local_ip()
            server_address = f"{local_ip}:{self.config_manager.port}"
            self._generate_qr_code(server_address)

            self._log(f"Service started on port {self.config_manager.port}")

        except Exception as e:
            self._log(f"Start failed: {str(e)}")
            self.status_label.setText("Start Failed")
            self.status_label.setStyleSheet("color: #f44336;")

    def _stop_server(self):
        if self.server:
            self.server.stop()
            self.server = None

        self.status_label.setText("Stopped")
        self.status_label.setStyleSheet("color: #666;")
        self.start_btn.setText("Start Service")
        self.qr_label.clear()
        self.qr_label.setText("Start service to show QR")

        self._log("Service stopped")

    def _on_file_received(self, tracking_number: str, filepath: str, size: str):
        self._log(f"Received: {tracking_number} ({size})")
        
        filename = Path(filepath).name
        
        self.tray_icon.showMessage(
            "Video Received",
            f"{filename}\nSize: {size}",
            QSystemTrayIcon.Information,
            3000
        )
        
        dialog = SuccessDialog(filename, size, self)
        dialog.exec_()

    def _on_error(self, error: str):
        self._log(f"Error: {error}")

    def _log(self, message: str):
        timestamp = datetime.now().strftime("%H:%M:%S")
        self.log_text.append(f"[{timestamp}] {message}")

    def _get_local_ip(self) -> str:
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.connect(("8.8.8.8", 80))
            ip = s.getsockname()[0]
            s.close()
            return ip
        except Exception:
            return "127.0.0.1"

    def _on_tray_activated(self, reason):
        if reason == QSystemTrayIcon.DoubleClick:
            self.show()

    def _quit_app(self):
        if self.server:
            self.server.stop()
        self.tray_icon.hide()
        QApplication.quit()

    def closeEvent(self, event):
        event.ignore()
        self.hide()
        self.tray_icon.showMessage(
            "Express Video Receiver",
            "Minimized to system tray",
            QSystemTrayIcon.Information,
            2000
        )


def main():
    app = QApplication(sys.argv)
    app.setQuitOnLastWindowClosed(False)

    window = MainWindow()
    window.show()

    sys.exit(app.exec_())


if __name__ == "__main__":
    main()