from pathlib import Path
import time

from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QPushButton, QLabel,
    QFrame, QScrollArea, QSizePolicy, QProgressDialog, QMessageBox,
)
from PyQt6.QtMultimedia import QMediaPlayer
from PyQt6.QtCore import Qt, pyqtSignal, QTimer, QSettings

from .models import VideoInfo, CropRect
from .video_preview import VideoPreview
from .trim_controls import TrimControls
from .video_processor import ExportThread
from .adb_helper import get_device_screen_size
from .theme import (
    PRIMARY, SECONDARY, SURFACE_VAR, ON_SURFACE_VAR,
    ON_BG, ERROR, SUCCESS, BG,
)


class EditorScreen(QWidget):
    back_requested = pyqtSignal()

    def __init__(self, parent=None):
        super().__init__(parent)
        self._info:   VideoInfo | None = None
        self._export: ExportThread | None = None

        self._settings = QSettings("MediaCrop", "MediaCrop")
        self._default_start_ms = int(self._settings.value("default_start_ms", 0))

        self._trim_start = 0
        self._trim_end   = 0
        self._active_preset = "9:16"

        self._setup_ui()

        # Position polling (100 ms, same cadence as Android)
        self._poll = QTimer()
        self._poll.setInterval(100)
        self._poll.timeout.connect(self._poll_position)

    # ── Build UI ──────────────────────────────────────────────────────────────

    def _setup_ui(self):
        root = QVBoxLayout(self)
        root.setContentsMargins(0, 0, 0, 0)
        root.setSpacing(0)

        # ── Top bar ───────────────────────────────────────────────────────────
        top = QWidget()
        top.setStyleSheet(f"background: {SURFACE_VAR};")
        top.setFixedHeight(48)
        tl = QHBoxLayout(top)
        tl.setContentsMargins(8, 4, 16, 4)

        self._btn_back = QPushButton("← Back")
        self._btn_back.setProperty("role", "back")
        self._btn_back.clicked.connect(self._on_back)
        tl.addWidget(self._btn_back)

        tl.addStretch()

        self._info_lbl = QLabel("")
        self._info_lbl.setStyleSheet(f"color: {ON_SURFACE_VAR}; font-size: 12px;")
        tl.addWidget(self._info_lbl)

        root.addWidget(top)

        # ── Video preview ─────────────────────────────────────────────────────
        self._preview = VideoPreview()
        self._preview.crop_changed.connect(self._on_crop_changed)
        self._preview.setSizePolicy(QSizePolicy.Policy.Expanding,
                                    QSizePolicy.Policy.Expanding)
        root.addWidget(self._preview, 55)

        # ── Controls panel ────────────────────────────────────────────────────
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        scroll.setStyleSheet(f"QScrollArea {{ border: none; background: {BG}; }}")

        ctrl_widget = QWidget()
        ctrl = QVBoxLayout(ctrl_widget)
        ctrl.setContentsMargins(12, 10, 12, 16)
        ctrl.setSpacing(10)

        # Preset buttons
        preset_row = QHBoxLayout()
        preset_row.setSpacing(6)
        self._preset_btns: dict[str, QPushButton] = {}
        for name, label in (("9:16", "9:16"), ("1:1", "■ Square"),
                             ("16:9", "16:9"), ("ADB", "📱 ADB")):
            btn = QPushButton(label)
            btn.setProperty("role", "adb" if name == "ADB" else "preset")
            btn.setProperty("active", "true" if name == "9:16" else "false")
            btn.clicked.connect(lambda _, n=name: self._on_preset(n))
            self._preset_btns[name] = btn
            preset_row.addWidget(btn)
        ctrl.addLayout(preset_row)

        self._divider(ctrl)

        # Playback controls
        pb_row = QHBoxLayout()
        pb_row.setAlignment(Qt.AlignmentFlag.AlignHCenter)
        pb_row.setSpacing(8)

        self._btn_prev = QPushButton("⏮")
        self._btn_prev.setProperty("role", "icon")
        self._btn_prev.setToolTip("Jump to IN point")
        self._btn_prev.clicked.connect(self._on_skip_prev)
        pb_row.addWidget(self._btn_prev)

        self._btn_play = QPushButton("▶")
        self._btn_play.setProperty("role", "play")
        self._btn_play.clicked.connect(self._on_play_pause)
        pb_row.addWidget(self._btn_play)

        self._btn_next = QPushButton("⏭")
        self._btn_next.setProperty("role", "icon")
        self._btn_next.setToolTip("Jump to OUT point")
        self._btn_next.clicked.connect(self._on_skip_next)
        pb_row.addWidget(self._btn_next)

        ctrl.addLayout(pb_row)

        # Set IN / Set OUT
        mark_row = QHBoxLayout()

        self._btn_set_in = QPushButton("◀ Set IN here")
        self._btn_set_in.setProperty("role", "adjust")
        self._btn_set_in.setStyleSheet(
            f"color: {SECONDARY}; font-size: 12px; background: transparent;"
        )
        self._btn_set_in.clicked.connect(self._on_set_in)
        mark_row.addWidget(self._btn_set_in, alignment=Qt.AlignmentFlag.AlignLeft)

        mark_row.addStretch()

        self._btn_set_out = QPushButton("Set OUT here ▶")
        self._btn_set_out.setProperty("role", "adjust")
        self._btn_set_out.setStyleSheet(
            f"color: {SECONDARY}; font-size: 12px; background: transparent;"
        )
        self._btn_set_out.clicked.connect(self._on_set_out)
        mark_row.addWidget(self._btn_set_out, alignment=Qt.AlignmentFlag.AlignRight)

        ctrl.addLayout(mark_row)

        self._divider(ctrl)

        # Trim controls
        self._trim = TrimControls()
        self._trim.trim_start_changed.connect(self._on_trim_start)
        self._trim.trim_end_changed.connect(self._on_trim_end)
        self._trim.seek_requested.connect(self._seek)
        ctrl.addWidget(self._trim)

        self._divider(ctrl)

        # Export
        self._btn_export = QPushButton("⬇  Export Video")
        self._btn_export.setProperty("role", "primary")
        self._btn_export.clicked.connect(self._on_export)
        ctrl.addWidget(self._btn_export)

        ctrl.addStretch()
        scroll.setWidget(ctrl_widget)
        root.addWidget(scroll, 45)

    @staticmethod
    def _divider(layout):
        f = QFrame()
        f.setObjectName("divider")
        f.setFixedHeight(1)
        f.setStyleSheet(f"background: {SURFACE_VAR};")
        layout.addWidget(f)

    # ── Load video ────────────────────────────────────────────────────────────

    def load_video(self, info: VideoInfo):
        self._info = info
        self._trim_start = min(self._default_start_ms,
                               max(0, info.duration_ms - 33))
        self._trim_end   = info.duration_ms

        self._info_lbl.setText(
            f"{info.display_width}×{info.display_height}  "
            f"{'%d:%02d' % divmod(info.duration_ms // 1000, 60)}"
        )

        self._trim.set_duration(info.duration_ms)
        self._trim.set_trim(self._trim_start, self._trim_end)

        self._preview.load(info)
        self._seek(self._trim_start)

        self._poll.start()

    # ── Polling ───────────────────────────────────────────────────────────────

    def _poll_position(self):
        player = self._preview.player
        pos = player.position()
        self._trim.set_position(pos)

        ps = player.playbackState()
        is_playing = ps == QMediaPlayer.PlaybackState.PlayingState
        self._btn_play.setText("⏸" if is_playing else "▶")

        if is_playing and pos >= self._trim_end:
            player.pause()
            self._seek(self._trim_start)

    # ── Preset buttons ────────────────────────────────────────────────────────

    def _on_preset(self, name: str):
        if name == "ADB":
            self._on_adb()
            return
        self._active_preset = name
        self._update_preset_buttons()
        aspect_map = {"9:16": 9.0/16.0, "1:1": 1.0, "16:9": 16.0/9.0}
        self._preview.set_aspect(aspect_map[name])

    def _on_adb(self):
        try:
            w, h = get_device_screen_size()
        except RuntimeError as exc:
            QMessageBox.warning(self, "ADB Error", str(exc))
            return
        self._active_preset = "ADB"
        self._update_preset_buttons()
        self._preview.set_aspect(w / h)

    def _update_preset_buttons(self):
        for name, btn in self._preset_btns.items():
            active = (name == self._active_preset)
            btn.setProperty("active", "true" if active else "false")
            btn.style().unpolish(btn)
            btn.style().polish(btn)

    # ── Playback ──────────────────────────────────────────────────────────────

    def _on_play_pause(self):
        player = self._preview.player
        if player.playbackState() == QMediaPlayer.PlaybackState.PlayingState:
            player.pause()
        else:
            if player.position() >= self._trim_end:
                self._seek(self._trim_start)
            player.play()

    def _on_skip_prev(self):
        self._seek(self._trim_start)

    def _on_skip_next(self):
        self._seek(self._trim_end)

    def _seek(self, ms: int):
        self._preview.player.setPosition(ms)
        self._trim.set_position(ms)

    def _on_set_in(self):
        pos = max(0, min(self._preview.player.position(), self._trim_end - 33))
        self._trim_start = pos
        self._trim.set_trim(self._trim_start, self._trim_end)

    def _on_set_out(self):
        pos = max(self._trim_start + 33,
                  min(self._preview.player.position(),
                      self._info.duration_ms if self._info else 0))
        self._trim_end = pos
        self._trim.set_trim(self._trim_start, self._trim_end)

    # ── Trim ──────────────────────────────────────────────────────────────────

    def _on_trim_start(self, ms: int):
        self._trim_start = ms

    def _on_trim_end(self, ms: int):
        self._trim_end = ms

    # ── Crop ─────────────────────────────────────────────────────────────────

    def _on_crop_changed(self, crop: CropRect):
        pass  # stored in VideoPreview; read at export time

    # ── Back ─────────────────────────────────────────────────────────────────

    def _on_back(self):
        self._poll.stop()
        self._preview.player.stop()
        self.back_requested.emit()

    # ── Export ────────────────────────────────────────────────────────────────

    def _on_export(self):
        if not self._info:
            return

        out_dir = Path.home() / "Videos" / "Cropped"
        out_dir.mkdir(parents=True, exist_ok=True)
        ts = int(time.time() * 1000)
        out_path = str(out_dir / f"MediaCrop_{ts}.mp4")

        self._btn_export.setEnabled(False)

        self._progress_dlg = QProgressDialog(
            "Exporting…", "Cancel", 0, 100, self
        )
        self._progress_dlg.setWindowTitle("Exporting Video")
        self._progress_dlg.setWindowModality(Qt.WindowModality.WindowModal)
        self._progress_dlg.setMinimumDuration(0)
        self._progress_dlg.setValue(0)
        self._progress_dlg.canceled.connect(self._on_export_cancel)

        self._export = ExportThread(
            video_info=self._info,
            crop_rect=self._preview.get_crop(),
            start_ms=self._trim_start,
            end_ms=self._trim_end,
            output_path=out_path,
            parent=self,
        )
        self._export.progress.connect(self._on_export_progress)
        self._export.completed.connect(self._on_export_done)
        self._export.failed.connect(self._on_export_fail)
        self._export.start()

    def _on_export_progress(self, frac: float):
        if hasattr(self, "_progress_dlg"):
            self._progress_dlg.setValue(int(frac * 100))

    def _on_export_done(self, path: str):
        if hasattr(self, "_progress_dlg"):
            self._progress_dlg.close()
        self._btn_export.setEnabled(True)
        msg = QMessageBox(self)
        msg.setWindowTitle("Export Complete")
        msg.setText(f"Video saved to:\n{path}")
        msg.setIcon(QMessageBox.Icon.Information)
        msg.addButton("Open Folder", QMessageBox.ButtonRole.ActionRole).clicked.connect(
            lambda: self._open_folder(path)
        )
        msg.addButton("OK", QMessageBox.ButtonRole.AcceptRole)
        msg.exec()

    def _on_export_fail(self, error: str):
        if hasattr(self, "_progress_dlg"):
            self._progress_dlg.close()
        self._btn_export.setEnabled(True)
        QMessageBox.critical(self, "Export Failed", error)

    def _on_export_cancel(self):
        if self._export and self._export.isRunning():
            self._export.terminate()
        self._btn_export.setEnabled(True)

    @staticmethod
    def _open_folder(path: str):
        import subprocess
        subprocess.Popen(["explorer", "/select,", path])
