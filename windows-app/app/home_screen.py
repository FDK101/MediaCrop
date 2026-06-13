from pathlib import Path

from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QPushButton, QLabel,
    QFileDialog, QSpinBox, QFrame, QSizePolicy,
)
from PyQt6.QtCore import Qt, pyqtSignal, QSettings
from PyQt6.QtGui import QDragEnterEvent, QDropEvent

from .models import VideoInfo
from .video_processor import probe_video
from .theme import (
    PRIMARY, SURFACE_VAR, ON_BG, ON_SURFACE_VAR, SECONDARY,
)


_VIDEO_FILTER = (
    "Video Files (*.mp4 *.mov *.mkv *.avi *.m4v *.wmv *.webm *.flv *.ts *.mts)"
    ";;All Files (*)"
)


class HomeScreen(QWidget):
    video_selected = pyqtSignal(object)   # VideoInfo

    def __init__(self, parent=None):
        super().__init__(parent)
        self._settings = QSettings("MediaCrop", "MediaCrop")
        self.setAcceptDrops(True)
        self._setup_ui()

    def _setup_ui(self):
        outer = QVBoxLayout(self)
        outer.setAlignment(Qt.AlignmentFlag.AlignCenter)

        card = QWidget()
        card.setFixedWidth(420)
        card.setStyleSheet(
            f"background: {SURFACE_VAR}; border-radius: 20px; padding: 32px;"
        )

        lay = QVBoxLayout(card)
        lay.setSpacing(20)
        lay.setContentsMargins(32, 32, 32, 32)
        lay.setAlignment(Qt.AlignmentFlag.AlignCenter)

        # Logo / title
        title = QLabel("Media Crop")
        title.setAlignment(Qt.AlignmentFlag.AlignCenter)
        title.setStyleSheet(
            f"color: {ON_BG}; font-size: 28px; font-weight: bold;"
            "background: transparent;"
        )
        lay.addWidget(title)

        subtitle = QLabel("Crop · Trim · Export")
        subtitle.setAlignment(Qt.AlignmentFlag.AlignCenter)
        subtitle.setStyleSheet(
            f"color: {ON_SURFACE_VAR}; font-size: 14px; background: transparent;"
        )
        lay.addWidget(subtitle)

        # Drop zone
        drop = QLabel("Drop a video here\nor")
        drop.setAlignment(Qt.AlignmentFlag.AlignCenter)
        drop.setStyleSheet(
            f"color: {ON_SURFACE_VAR}; font-size: 13px; background: transparent;"
        )
        lay.addWidget(drop)

        pick_btn = QPushButton("Pick Video File…")
        pick_btn.setProperty("role", "primary")
        pick_btn.setFixedHeight(48)
        pick_btn.clicked.connect(self._on_pick)
        lay.addWidget(pick_btn)

        # Divider
        f = QFrame()
        f.setFixedHeight(1)
        f.setStyleSheet(f"background: #3a3a3a; margin: 4px 0;")
        lay.addWidget(f)

        # Default start time
        ds_row = QHBoxLayout()
        ds_lbl = QLabel("Default start time (ms)")
        ds_lbl.setStyleSheet(
            f"color: {ON_SURFACE_VAR}; font-size: 12px; background: transparent;"
        )
        ds_row.addWidget(ds_lbl)
        ds_row.addStretch()

        self._spin = QSpinBox()
        self._spin.setRange(0, 999999)
        self._spin.setSingleStep(100)
        self._spin.setSuffix(" ms")
        self._spin.setValue(int(self._settings.value("default_start_ms", 0)))
        self._spin.valueChanged.connect(self._on_default_start_changed)
        self._spin.setFixedWidth(120)
        ds_row.addWidget(self._spin)
        lay.addLayout(ds_row)

        ds_hint = QLabel(
            "When a video loads, the IN point starts here.\n"
            "Useful for skipping a camera intro every time."
        )
        ds_hint.setWordWrap(True)
        ds_hint.setAlignment(Qt.AlignmentFlag.AlignCenter)
        ds_hint.setStyleSheet(
            f"color: {ON_SURFACE_VAR}; font-size: 11px; background: transparent;"
        )
        lay.addWidget(ds_hint)

        self._status_lbl = QLabel("")
        self._status_lbl.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self._status_lbl.setWordWrap(True)
        self._status_lbl.setStyleSheet(
            "color: #EF4444; font-size: 12px; background: transparent;"
        )
        lay.addWidget(self._status_lbl)

        outer.addWidget(card)

    # ── Pick / drag ────────────────────────────────────────────────────────────

    def _on_pick(self):
        path, _ = QFileDialog.getOpenFileName(
            self, "Open Video", str(Path.home()), _VIDEO_FILTER
        )
        if path:
            self._try_load(path)

    def dragEnterEvent(self, ev: QDragEnterEvent):
        if ev.mimeData().hasUrls():
            ev.acceptProposedAction()

    def dropEvent(self, ev: QDropEvent):
        urls = ev.mimeData().urls()
        if urls:
            self._try_load(urls[0].toLocalFile())

    def _try_load(self, path: str):
        self._status_lbl.setText("Reading file…")
        try:
            info = probe_video(path)
        except Exception as exc:
            self._status_lbl.setText(str(exc))
            return
        self._status_lbl.setText("")
        self.video_selected.emit(info)

    # ── Settings ───────────────────────────────────────────────────────────────

    def _on_default_start_changed(self, value: int):
        self._settings.setValue("default_start_ms", value)
