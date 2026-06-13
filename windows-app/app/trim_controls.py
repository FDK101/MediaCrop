from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QPushButton,
    QSlider, QSizePolicy,
)
from PyQt6.QtCore import Qt, pyqtSignal, QRect, QRectF, QPointF
from PyQt6.QtGui import QPainter, QColor, QPen, QFont
from .theme import (
    TRACK_ACTIVE, TRACK_INACTIVE, THUMB, SECONDARY,
    ON_SURFACE_VAR, ON_BG, SURFACE_VAR, PRIMARY,
)


def fmt_time(ms: int) -> str:
    ms = max(0, int(ms))
    mins = ms // 60000
    secs = (ms % 60000) // 1000
    millis = ms % 1000
    return f"{mins:02d}:{secs:02d}.{millis:03d}"


# ── Custom range slider ───────────────────────────────────────────────────────

class RangeSlider(QWidget):
    """Two-handle trim slider + playback-position indicator."""
    start_changed  = pyqtSignal(int)   # trimStartMs
    end_changed    = pyqtSignal(int)   # trimEndMs
    seek_requested = pyqtSignal(int)   # seekMs (click on track)

    _THUMB_W = 10
    _THUMB_H = 22
    _TRACK_H =  6
    _MARGIN  = 10   # horizontal padding so thumbs don't clip

    def __init__(self, parent=None):
        super().__init__(parent)
        self._duration = 1
        self._start    = 0
        self._end      = 1
        self._position = 0
        self._drag     = None   # 'start' | 'end' | 'seek'
        self.setFixedHeight(36)
        self.setMouseTracking(True)

    # ── setters ──────────────────────────────────────────────────────────────

    def set_duration(self, ms: int):
        self._duration = max(1, ms)
        self._end = self._duration
        self.update()

    def set_trim(self, start: int, end: int):
        self._start = start; self._end = end; self.update()

    def set_position(self, ms: int):
        self._position = ms; self.update()

    # ── geometry helpers ─────────────────────────────────────────────────────

    def _track_rect(self) -> QRect:
        m  = self._MARGIN
        th = self._TRACK_H
        y  = (self.height() - th) // 2
        return QRect(m, y, self.width() - 2*m, th)

    def _ms_to_x(self, ms: int) -> float:
        tr = self._track_rect()
        return tr.x() + (ms / self._duration) * tr.width()

    def _x_to_ms(self, x: float) -> int:
        tr = self._track_rect()
        ratio = (x - tr.x()) / tr.width()
        return int(max(0.0, min(1.0, ratio)) * self._duration)

    # ── paint ─────────────────────────────────────────────────────────────────

    def paintEvent(self, _):
        p = QPainter(self)
        p.setRenderHint(QPainter.RenderHint.Antialiasing)
        tr = self._track_rect()

        # Inactive track
        p.setBrush(QColor(TRACK_INACTIVE))
        p.setPen(Qt.PenStyle.NoPen)
        p.drawRoundedRect(tr, 3, 3)

        # Active region
        sx = self._ms_to_x(self._start)
        ex = self._ms_to_x(self._end)
        act = QRect(int(sx), tr.y(), int(ex - sx), tr.height())
        p.setBrush(QColor(TRACK_ACTIVE))
        p.drawRect(act)

        # Position line
        px = int(self._ms_to_x(self._position))
        p.setPen(QPen(QColor(SECONDARY), 2))
        y0 = tr.y() - 5;  y1 = tr.bottom() + 5
        p.drawLine(px, y0, px, y1)

        # Thumbs
        tw = self._THUMB_W;  th = self._THUMB_H
        cy = self.height() // 2
        for mx in (sx, ex):
            r = QRect(int(mx) - tw//2, cy - th//2, tw, th)
            p.setPen(Qt.PenStyle.NoPen)
            p.setBrush(QColor(THUMB))
            p.drawRoundedRect(r, 4, 4)

        p.end()

    # ── mouse ─────────────────────────────────────────────────────────────────

    def mousePressEvent(self, ev):
        x  = ev.position().x()
        sx = self._ms_to_x(self._start)
        ex = self._ms_to_x(self._end)
        tw = self._THUMB_W + 5
        if abs(x - sx) <= tw:
            self._drag = "start"
        elif abs(x - ex) <= tw:
            self._drag = "end"
        else:
            self._drag = "seek"
            self.seek_requested.emit(self._x_to_ms(x))

    def mouseMoveEvent(self, ev):
        if not self._drag:
            return
        ms = self._x_to_ms(ev.position().x())
        if self._drag == "start":
            ns = max(0, min(ms, self._end - 33))
            if ns != self._start:
                self._start = ns; self.start_changed.emit(ns); self.update()
        elif self._drag == "end":
            ne = min(self._duration, max(ms, self._start + 33))
            if ne != self._end:
                self._end = ne; self.end_changed.emit(ne); self.update()
        elif self._drag == "seek":
            self.seek_requested.emit(ms)

    def mouseReleaseEvent(self, _):
        self._drag = None


# ── Micro-adjust row ──────────────────────────────────────────────────────────

class AdjustRow(QWidget):
    """Label + time display + ±1ms/±100ms/±1s buttons."""
    adjusted = pyqtSignal(int)   # new absolute value in ms

    def __init__(self, label: str, parent=None):
        super().__init__(parent)
        self._value_ms = 0

        lay = QHBoxLayout(self)
        lay.setContentsMargins(10, 6, 10, 6)
        lay.setSpacing(4)

        lbl = QLabel(label)
        lbl.setFixedWidth(38)
        lbl.setStyleSheet(f"color: {ON_SURFACE_VAR}; font-size: 11px;")
        lay.addWidget(lbl)

        self._time_lbl = QLabel(fmt_time(0))
        self._time_lbl.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self._time_lbl.setStyleSheet(
            f"color: {ON_BG}; font-family: Consolas, monospace; font-size: 14px;"
        )
        self._time_lbl.setSizePolicy(QSizePolicy.Policy.Expanding,
                                     QSizePolicy.Policy.Preferred)
        lay.addWidget(self._time_lbl)

        for label_txt, delta in (
            ("-1s", -1000), ("-100", -100), ("-1", -1),
            ("+1", 1),      ("+100", 100),  ("+1s", 1000),
        ):
            btn = QPushButton(label_txt)
            btn.setProperty("role", "adjust")
            btn.setFixedSize(38, 26)
            btn.clicked.connect(lambda _, d=delta: self._on_adjust(d))
            lay.addWidget(btn)

        self.setStyleSheet(f"background-color: {SURFACE_VAR}; border-radius: 10px;")

    def set_value(self, ms: int):
        self._value_ms = ms
        self._time_lbl.setText(fmt_time(ms))

    def _on_adjust(self, delta: int):
        self.adjusted.emit(self._value_ms + delta)


# ── Full TrimControls ─────────────────────────────────────────────────────────

class TrimControls(QWidget):
    trim_start_changed = pyqtSignal(int)
    trim_end_changed   = pyqtSignal(int)
    seek_requested     = pyqtSignal(int)

    def __init__(self, parent=None):
        super().__init__(parent)
        self._duration   = 1
        self._trim_start = 0
        self._trim_end   = 1
        self._position   = 0

        lay = QVBoxLayout(self)
        lay.setContentsMargins(12, 8, 12, 8)
        lay.setSpacing(8)

        # ── Seek slider (position) ────────────────────────────────────────────
        seek_row = QHBoxLayout()
        self._pos_lbl = QLabel("▶  00:00.000")
        self._pos_lbl.setStyleSheet(
            f"color: {ON_SURFACE_VAR}; font-family: Consolas, monospace; font-size: 12px;"
        )
        seek_row.addWidget(self._pos_lbl)
        seek_row.addStretch()
        lay.addLayout(seek_row)

        self._seek_slider = QSlider(Qt.Orientation.Horizontal)
        self._seek_slider.setMinimum(0)
        self._seek_slider.setMaximum(1000)
        self._seek_slider.valueChanged.connect(self._on_seek_slider)
        lay.addWidget(self._seek_slider)

        # ── Range slider ──────────────────────────────────────────────────────
        self._range_slider = RangeSlider()
        self._range_slider.start_changed.connect(self._on_range_start)
        self._range_slider.end_changed.connect(self._on_range_end)
        self._range_slider.seek_requested.connect(self._on_range_seek)
        lay.addWidget(self._range_slider)

        # Time labels under range slider
        ts_row = QHBoxLayout()
        self._in_lbl  = QLabel("00:00.000")
        self._dur_lbl = QLabel("00:00.000")
        self._out_lbl = QLabel("00:00.000")
        for lbl in (self._in_lbl, self._dur_lbl, self._out_lbl):
            lbl.setStyleSheet(
                f"color: {THUMB}; font-family: Consolas, monospace; font-size: 11px;"
            )
        self._dur_lbl.setStyleSheet(
            f"color: {ON_SURFACE_VAR}; font-family: Consolas, monospace; font-size: 11px;"
        )
        ts_row.addWidget(self._in_lbl)
        ts_row.addStretch()
        ts_row.addWidget(self._dur_lbl)
        ts_row.addStretch()
        ts_row.addWidget(self._out_lbl)
        lay.addLayout(ts_row)

        # ── Micro-adjust rows ─────────────────────────────────────────────────
        self._play_row = AdjustRow("PLAY")
        self._play_row.adjusted.connect(self._on_play_adjust)
        lay.addWidget(self._play_row)

        self._in_row = AdjustRow("IN")
        self._in_row.adjusted.connect(self._on_in_adjust)
        lay.addWidget(self._in_row)

        self._out_row = AdjustRow("OUT")
        self._out_row.adjusted.connect(self._on_out_adjust)
        lay.addWidget(self._out_row)

        # ── Duration ─────────────────────────────────────────────────────────
        dur_row = QHBoxLayout()
        dur_label = QLabel("Duration")
        dur_label.setStyleSheet(f"color: {ON_SURFACE_VAR}; font-size: 12px;")
        self._clip_dur_lbl = QLabel("00:00.000")
        self._clip_dur_lbl.setStyleSheet(
            f"color: {SECONDARY}; font-family: Consolas, monospace; font-size: 12px;"
        )
        dur_row.addWidget(dur_label)
        dur_row.addStretch()
        dur_row.addWidget(self._clip_dur_lbl)
        lay.addLayout(dur_row)

        self._updating = False

    # ── Public setters ────────────────────────────────────────────────────────

    def set_duration(self, ms: int):
        self._duration = max(1, ms)
        self._seek_slider.setMaximum(ms)
        self._range_slider.set_duration(ms)

    def set_trim(self, start: int, end: int):
        self._trim_start = start
        self._trim_end   = end
        self._refresh_labels()
        self._range_slider.set_trim(start, end)
        self._in_row.set_value(start)
        self._out_row.set_value(end)

    def set_position(self, ms: int):
        self._position = ms
        self._pos_lbl.setText(f"▶  {fmt_time(ms)}")
        self._play_row.set_value(ms)
        self._range_slider.set_position(ms)
        self._updating = True
        self._seek_slider.setValue(ms)
        self._updating = False

    # ── Slots ─────────────────────────────────────────────────────────────────

    def _on_seek_slider(self, value: int):
        if not self._updating:
            self.seek_requested.emit(value)

    def _on_range_start(self, ms: int):
        self._trim_start = ms
        self._in_row.set_value(ms)
        self._refresh_labels()
        self.trim_start_changed.emit(ms)

    def _on_range_end(self, ms: int):
        self._trim_end = ms
        self._out_row.set_value(ms)
        self._refresh_labels()
        self.trim_end_changed.emit(ms)

    def _on_range_seek(self, ms: int):
        self.seek_requested.emit(ms)

    def _on_play_adjust(self, ms: int):
        ms = max(0, min(ms, self._duration))
        self.seek_requested.emit(ms)

    def _on_in_adjust(self, ms: int):
        ms = max(0, min(ms, self._trim_end - 33))
        self._trim_start = ms
        self._in_row.set_value(ms)
        self._range_slider.set_trim(self._trim_start, self._trim_end)
        self._refresh_labels()
        self.trim_start_changed.emit(ms)

    def _on_out_adjust(self, ms: int):
        ms = max(self._trim_start + 33, min(ms, self._duration))
        self._trim_end = ms
        self._out_row.set_value(ms)
        self._range_slider.set_trim(self._trim_start, self._trim_end)
        self._refresh_labels()
        self.trim_end_changed.emit(ms)

    def _refresh_labels(self):
        self._in_lbl.setText(fmt_time(self._trim_start))
        self._out_lbl.setText(fmt_time(self._trim_end))
        self._clip_dur_lbl.setText(fmt_time(self._trim_end - self._trim_start))
