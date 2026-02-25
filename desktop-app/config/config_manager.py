import json
import os
from pathlib import Path
from typing import Optional


class ConfigManager:
    DEFAULT_CONFIG = {
        "save_path": str(Path.home() / "Videos" / "ExpressVideo"),
        "port": 8080,
        "auto_start": True
    }

    def __init__(self, config_dir: Optional[str] = None):
        if config_dir:
            self.config_dir = Path(config_dir)
        else:
            self.config_dir = Path.home() / ".express_video"
        self.config_file = self.config_dir / "config.json"
        self._ensure_config_dir()
        self._config = self._load_config()

    def _ensure_config_dir(self):
        self.config_dir.mkdir(parents=True, exist_ok=True)

    def _load_config(self) -> dict:
        if self.config_file.exists():
            try:
                with open(self.config_file, 'r', encoding='utf-8') as f:
                    config = json.load(f)
                    return {**self.DEFAULT_CONFIG, **config}
            except (json.JSONDecodeError, IOError):
                return self.DEFAULT_CONFIG.copy()
        return self.DEFAULT_CONFIG.copy()

    def save_config(self):
        with open(self.config_file, 'w', encoding='utf-8') as f:
            json.dump(self._config, f, indent=2, ensure_ascii=False)

    @property
    def save_path(self) -> str:
        return self._config.get("save_path", self.DEFAULT_CONFIG["save_path"])

    @save_path.setter
    def save_path(self, path: str):
        self._config["save_path"] = path
        self.save_config()

    @property
    def port(self) -> int:
        return self._config.get("port", self.DEFAULT_CONFIG["port"])

    @port.setter
    def port(self, port: int):
        self._config["port"] = port
        self.save_config()

    @property
    def auto_start(self) -> bool:
        return self._config.get("auto_start", self.DEFAULT_CONFIG["auto_start"])

    @auto_start.setter
    def auto_start(self, auto: bool):
        self._config["auto_start"] = auto
        self.save_config()

    def get(self, key: str, default=None):
        return self._config.get(key, default)

    def set(self, key: str, value):
        self._config[key] = value
        self.save_config()
