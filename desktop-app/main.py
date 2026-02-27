import socket
import sys
import os
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
    QFrame,
    QMessageBox
)
from PyQt5.QtGui import QIcon, QFont, QPixmap, QImage
from PyQt5.QtCore import Qt as QtCoreQt

from config.config_manager import ConfigManager
from server.http_server import HttpServer, is_port_in_use

import qrcode
from io import BytesIO


def cleanup_old_instances():
    """清理后台残留的 Python 进程"""
    try:
        import psutil
    except ImportError:
        print("[警告] 未安装 psutil 库，跳过进程清理")
        return []
        
    current_pid = os.getpid()
    cleaned = []
    
    for proc in psutil.process_iter(['pid', 'name']):
        try:
            p_name = proc.info.get('name')
            if p_name and proc.info['pid'] != current_pid and 'python' in p_name.lower():
                p_cmdline = proc.cmdline()
                if p_cmdline:
                    cmdline = ' '.join(p_cmdline)
                    if 'main.py' in cmdline or 'desktop-app' in cmdline:
                        print(f"清理旧进程：{proc.info['pid']}")
                        proc.terminate()
                        cleaned.append(proc.info['pid'])
        except (psutil.NoSuchProcess, psutil.AccessDenied):
            pass
    
    return cleaned


class SuccessDialog(QDialog):
    def __init__(self, filename: str, size: str, parent=None):
        super().__init__(parent)
        self.setWindowTitle("视频接收成功")
        self.setFixedSize(300, 150)
        self.setWindowFlags(self.windowFlags() | QtCoreQt.WindowStaysOnTopHint)
        
        layout = QVBoxLayout(self)
        layout.setSpacing(10)
        
        title_label = QLabel("视频接收成功")
        title_label.setFont(QFont("Arial", 14, QFont.Bold))
        title_label.setStyleSheet("color: #4CAF50;")
        title_label.setAlignment(QtCoreQt.AlignCenter)
        layout.addWidget(title_label)
        
        file_label = QLabel(f"文件：{filename}")
        file_label.setAlignment(QtCoreQt.AlignCenter)
        layout.addWidget(file_label)
        
        size_label = QLabel(f"大小：{size}")
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
    file_received_signal = pyqtSignal(str, str, str)

    def __init__(self):
        super().__init__()
        
        self.config_manager = ConfigManager()
        self.server: Optional[HttpServer] = None
        self.server_thread: Optional[ServerThread] = None

        self._init_ui()
        self._init_tray()
        self._load_config()
        
        # 连接信号
        self.file_received_signal.connect(self._handle_file_received_ui)

    def _init_ui(self):
        self.setWindowTitle("快递视频接收器")
        self.setMinimumSize(550, 600)

        central_widget = QWidget()
        self.setCentralWidget(central_widget)

        layout = QVBoxLayout(central_widget)
        layout.setSpacing(12)
        layout.setContentsMargins(20, 20, 20, 20)

        status_group = QGroupBox("服务状态")
        status_layout = QVBoxLayout(status_group)

        self.status_label = QLabel("已停止")
        self.status_label.setFont(QFont("Arial", 14))
        self.status_label.setStyleSheet("color: #666;")
        status_layout.addWidget(self.status_label)

        self.ip_label = QLabel(f"IP: {self._get_local_ip()}")
        status_layout.addWidget(self.ip_label)

        self.port_label = QLabel(f"端口：{self.config_manager.port}")
        status_layout.addWidget(self.port_label)

        layout.addWidget(status_group)

        qr_group = QGroupBox("二维码 (手机扫描配置)")
        qr_layout = QVBoxLayout(qr_group)
        qr_layout.setAlignment(QtCoreQt.AlignCenter)

        self.qr_label = QLabel()
        self.qr_label.setFixedSize(200, 200)
        self.qr_label.setAlignment(QtCoreQt.AlignCenter)
        self.qr_label.setStyleSheet("background-color: white; border: 1px solid #ccc;")
        self.qr_label.setText("启动服务后显示二维码")
        qr_layout.addWidget(self.qr_label)

        self.qr_hint = QLabel("使用手机 APP 扫描二维码配置服务器")
        self.qr_hint.setStyleSheet("color: #666; font-size: 11px;")
        qr_layout.addWidget(self.qr_hint)

        layout.addWidget(qr_group)

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

        # 自动保存状态提示
        self.save_status_label = QLabel("")
        self.save_status_label.setStyleSheet("color: #4CAF50; font-size: 11px;")
        settings_layout.addWidget(self.save_status_label)

        layout.addWidget(settings_group)

        control_layout = QHBoxLayout()
        self.start_btn = QPushButton("启动服务")
        self.start_btn.clicked.connect(self._toggle_server)
        self.start_btn.setMinimumHeight(40)
        control_layout.addWidget(self.start_btn)

        layout.addLayout(control_layout)

        log_group = QGroupBox("已接收的视频")
        log_layout = QVBoxLayout(log_group)

        self.log_text = QTextEdit()
        self.log_text.setReadOnly(True)
        self.log_text.setMaximumHeight(120)
        log_layout.addWidget(self.log_text)

        layout.addWidget(log_group)

        # 自动保存连接
        self.path_edit.textChanged.connect(self._auto_save_settings)
        self.port_spin.valueChanged.connect(self._auto_save_settings)

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
        self.tray_icon.setToolTip("快递视频接收器")

        # 设置一个默认图标，避免某些系统显示异常
        pixmap = QPixmap(32, 32)
        pixmap.fill(QtCoreQt.transparent)
        self.tray_icon.setIcon(QIcon(pixmap))

        tray_menu = QMenu()

        show_action = QAction("显示窗口", self)
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
        self.port_label.setText(f"端口：{self.config_manager.port}")

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

    def _auto_save_settings(self):
        """自动保存设置"""
        self.config_manager.save_path = self.path_edit.text()
        self.config_manager.port = self.port_spin.value()
        self.port_label.setText(f"端口：{self.port_spin.value()}")
        
        # 显示保存提示
        self.save_status_label.setText("✓ 已自动保存")
        QTimer.singleShot(2000, lambda: self.save_status_label.setText(""))

    def _apply_settings(self):
        """废弃：现在自动保存，此方法保留仅为兼容"""
        self._auto_save_settings()

    def _toggle_server(self):
        if self.server and self.server.is_running:
            self._stop_server()
        else:
            self._start_server()

    def _start_server(self):
        try:
            # 使用当前输入框的最新配置
            save_path = self.path_edit.text()
            port = self.port_spin.value()
            
            # 确保配置已保存
            self.config_manager.save_path = save_path
            self.config_manager.port = port
            
            if is_port_in_use(port):
                raise Exception(f"端口 {port} 已被占用，请关闭其他程序或更换端口")
            
            self.server = HttpServer(
                save_path=save_path,
                port=port,
                on_file_received=self._on_file_received,
                on_error=self._on_error
            )

            self.server.start()

            self.status_label.setText("运行中")
            self.status_label.setStyleSheet("color: #4CAF50; font-weight: bold;")
            self.start_btn.setText("停止服务")

            local_ip = self._get_local_ip()
            server_address = f"{local_ip}:{port}"
            self._generate_qr_code(server_address)

            self._log(f"服务已启动，端口：{port}")

        except Exception as e:
            error_msg = str(e)
            if "端口" in error_msg and "占用" in error_msg:
                error_msg += "\n\n请点击\"设置\"按钮更换端口，或关闭占用该端口的程序"
                QMessageBox.critical(self, "端口被占用", error_msg)
            
            self._log(f"启动失败：{error_msg}")
            self.status_label.setText("启动失败")
            self.status_label.setStyleSheet("color: #f44336;")

    def _stop_server(self):
        if self.server:
            self.server.stop()
            self.server = None

        self.status_label.setText("已停止")
        self.status_label.setStyleSheet("color: #666;")
        self.start_btn.setText("启动服务")
        self.qr_label.clear()
        self.qr_label.setText("启动服务后显示二维码")

        self._log("服务已停止")

    def _on_file_received(self, tracking_number: str, filepath: str, size: str):
        # 此方法在服务器线程中调用，发射信号到主线程
        self.file_received_signal.emit(tracking_number, filepath, size)

    def _handle_file_received_ui(self, tracking_number: str, filepath: str, size: str):
        # 此方法在主线程执行，安全进行 UI 操作
        filename = Path(filepath).name
        self._log(f"已接收：{filename} ({size})")
        
        self.tray_icon.showMessage(
            "视频已接收",
            f"{filename}\n大小：{size}",
            QSystemTrayIcon.Information,
            3000
        )
        
        dialog = SuccessDialog(filename, size, self)
        dialog.setAttribute(QtCoreQt.WA_DeleteOnClose)
        dialog.show()  # 使用 show 而不是 exec_，这样不会阻塞后续操作

    def _on_error(self, error: str):
        self._log(f"错误：{error}")

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
            "快递视频接收器",
            "已最小化到系统托盘",
            QSystemTrayIcon.Information,
            2000
        )


def main():
    print("正在启动程序...")
    cleaned = cleanup_old_instances()
    if cleaned:
        print(f"已清理 {len(cleaned)} 个旧进程")
    
    app = QApplication(sys.argv)
    app.setQuitOnLastWindowClosed(False)

    window = MainWindow()
    window.show()

    sys.exit(app.exec_())


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        import traceback
        error_msg = traceback.format_exc()
        print(error_msg)
        with open("error_log.txt", "w", encoding="utf-8") as f:
            f.write(error_msg)
        input("程序运行出错，按回车键退出...")