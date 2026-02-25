import socket
import sys
from pathlib import Path
from typing import Optional

from PyQt5.QtCore import Qt, QThread, pyqtSignal
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
    QAction
)
from PyQt5.QtGui import QIcon, QFont

from config.config_manager import ConfigManager
from server.http_server import HttpServer


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
        self.setWindowTitle("快递视频接收服务")
        self.setMinimumSize(500, 400)

        central_widget = QWidget()
        self.setCentralWidget(central_widget)

        layout = QVBoxLayout(central_widget)
        layout.setSpacing(16)
        layout.setContentsMargins(20, 20, 20, 20)

        status_group = QGroupBox("服务状态")
        status_layout = QVBoxLayout(status_group)

        self.status_label = QLabel("未启动")
        self.status_label.setFont(QFont("Arial", 14))
        self.status_label.setStyleSheet("color: #666;")
        status_layout.addWidget(self.status_label)

        self.ip_label = QLabel(f"本机IP: {self._get_local_ip()}")
        status_layout.addWidget(self.ip_label)

        self.port_label = QLabel(f"端口: {self.config_manager.port}")
        status_layout.addWidget(self.port_label)

        layout.addWidget(status_group)

        settings_group = QGroupBox("设置")
        settings_layout = QVBoxLayout(settings_group)

        path_layout = QHBoxLayout()
        path_layout.addWidget(QLabel("保存路径:"))

        self.path_edit = QLineEdit()
        self.path_edit.setReadOnly(True)
        path_layout.addWidget(self.path_edit)

        self.browse_btn = QPushButton("浏览...")
        self.browse_btn.clicked.connect(self._browse_folder)
        path_layout.addWidget(self.browse_btn)

        settings_layout.addLayout(path_layout)

        port_layout = QHBoxLayout()
        port_layout.addWidget(QLabel("端口:"))

        self.port_spin = QSpinBox()
        self.port_spin.setRange(1, 65535)
        self.port_spin.setValue(self.config_manager.port)
        port_layout.addWidget(self.port_spin)

        port_layout.addStretch()
        settings_layout.addLayout(port_layout)

        layout.addWidget(settings_group)

        control_layout = QHBoxLayout()
        self.start_btn = QPushButton("启动服务")
        self.start_btn.clicked.connect(self._toggle_server)
        self.start_btn.setMinimumHeight(40)
        control_layout.addWidget(self.start_btn)

        self.apply_btn = QPushButton("应用设置")
        self.apply_btn.clicked.connect(self._apply_settings)
        self.apply_btn.setMinimumHeight(40)
        control_layout.addWidget(self.apply_btn)

        layout.addLayout(control_layout)

        log_group = QGroupBox("接收记录")
        log_layout = QVBoxLayout(log_group)

        self.log_text = QTextEdit()
        self.log_text.setReadOnly(True)
        self.log_text.setMaximumHeight(150)
        log_layout.addWidget(self.log_text)

        layout.addWidget(log_group)

    def _init_tray(self):
        self.tray_icon = QSystemTrayIcon(self)
        self.tray_icon.setIcon(self.style().standardIcon(
            self.style().SP_ComputerIcon
        ))
        self.tray_icon.setToolTip("快递视频接收服务")

        tray_menu = QMenu()

        show_action = QAction("显示主窗口", self)
        show_action.triggered.connect(self.show)
        tray_menu.addAction(show_action)

        quit_action = QAction("退出", self)
        quit_action.triggered.connect(self._quit_app)
        tray_menu.addAction(quit_action)

        self.tray_icon.setContextMenu(tray_menu)
        self.tray_icon.activated.connect(self._on_tray_activated)
        self.tray_icon.show()

    def _load_config(self):
        self.path_edit.setText(self.config_manager.save_path)
        self.port_spin.setValue(self.config_manager.port)
        self.port_label.setText(f"端口: {self.config_manager.port}")

        Path(self.config_manager.save_path).mkdir(parents=True, exist_ok=True)

        if self.config_manager.auto_start:
            self._start_server()

    def _browse_folder(self):
        folder = QFileDialog.getExistingDirectory(
            self,
            "选择保存路径",
            self.path_edit.text()
        )
        if folder:
            self.path_edit.setText(folder)

    def _apply_settings(self):
        self.config_manager.save_path = self.path_edit.text()
        self.config_manager.port = self.port_spin.value()
        self.port_label.setText(f"端口: {self.port_spin.value()}")

        if self.server and self.server.is_running:
            self._log("设置已保存，需要重启服务生效")
        else:
            self._log("设置已保存")

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

            self.status_label.setText("运行中")
            self.status_label.setStyleSheet("color: #4CAF50; font-weight: bold;")
            self.start_btn.setText("停止服务")

            self._log(f"服务已启动，监听端口 {self.config_manager.port}")

        except Exception as e:
            self._log(f"启动失败: {str(e)}")
            self.status_label.setText("启动失败")
            self.status_label.setStyleSheet("color: #f44336;")

    def _stop_server(self):
        if self.server:
            self.server.stop()
            self.server = None

        self.status_label.setText("已停止")
        self.status_label.setStyleSheet("color: #666;")
        self.start_btn.setText("启动服务")

        self._log("服务已停止")

    def _on_file_received(self, tracking_number: str, filepath: str, size: str):
        self._log(f"收到视频: {tracking_number} ({size}) -> {filepath}")

    def _on_error(self, error: str):
        self._log(f"错误: {error}")

    def _log(self, message: str):
        from datetime import datetime
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
            "快递视频接收服务",
            "程序已最小化到系统托盘",
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
