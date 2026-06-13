from typing import Optional
from PyQt6.QtWidgets import QWidget
from PyQt6.QtMultimedia import QMediaPlayer, QVideoSink, QVideoFrame, QAudioOutput
from PyQt6.QtCore import Qt, pyqtSignal, QUrl, QRectF, QRect, QPointF, QSizeF
from PyQt6.QtGui import (
    QPainter, QPixmap, QImage, QColor, QPen, QFont, QFontMetrics,
    QCursor,
)
from .models import CropRect, VideoInfo, DragMode
from .theme import (
    SECONDARY, TRACK_ACTIVE, ON_BG, SURFACE_VAR,
)

_CURSORS = {
    DragMode.MOVE: Qt.CursorShape.SizeAllCursor,
    DragMode.TL:   Qt.CursorShape.SizeFDiagCursor,
    DragMode.BR:   Qt.CursorShape.SizeFDiagCursor,
    DragMode.TR:   Qt.CursorShape.SizeBDiagCursor,
    DragMode.BL:   Qt.CursorShape.SizeBDiagCursor,
    DragMode.T:    Qt.CursorShape.SizeVerCursor,
    DragMode.B:    Qt.CursorShape.SizeVerCursor,
    DragMode.L:    Qt.CursorShape.SizeHorCursor,
    DragMode.R:    Qt.CursorShape.SizeHorCursor,
}

_MIN_CROP = 0.05
_HIT_PX   = 20


class VideoPreview(QWidget):
    """
    Single widget: renders decoded video frames via QVideoSink and draws
    the crop overlay on top using QPainter.  Handles crop drag entirely
    within itself; emits crop_changed when the rect is updated.
    """

    crop_changed = pyqtSignal(object)   # CropRect

    def __init__(self, parent=None):
        super().__init__(parent)
        self.setMinimumSize(320, 240)
        self.setStyleSheet("background: black;")
        self.setMouseTracking(True)

        self._pixmap:     Optional[QPixmap] = None
        self._video_info: Optional[VideoInfo] = None
        self._crop        = CropRect()
        self._aspect      = 9.0 / 16.0   # target output aspect (w/h)

        self._drag_mode  = DragMode.NONE
        self._drag_start: Optional[QPointF] = None
        self._drag_crop0: Optional[CropRect] = None

        # ── Player ───────────────────────────────────────────────────────────
        self._audio = QAudioOutput()
        self._audio.setVolume(0.0)          # always muted in preview

        self._player = QMediaPlayer()
        self._player.setAudioOutput(self._audio)

        self._sink = QVideoSink()
        self._player.setVideoOutput(self._sink)
        self._sink.videoFrameChanged.connect(self._on_frame,
                                             Qt.ConnectionType.QueuedConnection)

    # ── Public API ────────────────────────────────────────────────────────────

    @property
    def player(self) -> QMediaPlayer:
        return self._player

    def load(self, info: VideoInfo):
        self._video_info = info
        self._player.setSource(QUrl.fromLocalFile(info.path))
        self._player.pause()
        self._recalculate_crop()

    def set_crop(self, crop: CropRect):
        self._crop = crop
        self.update()

    def get_crop(self) -> CropRect:
        return self._crop

    def set_aspect(self, aspect: float):
        """aspect = width/height of desired output (e.g. 9/16 for portrait)."""
        self._aspect = aspect
        self._recalculate_crop()

    # ── Internal ──────────────────────────────────────────────────────────────

    def _recalculate_crop(self):
        if not self._video_info:
            return
        va = self._video_info.display_aspect
        sa = self._aspect
        if sa <= va:
            cw = sa / va; ch = 1.0
        else:
            cw = 1.0;     ch = va / sa
        cw = min(cw, 1.0); ch = min(ch, 1.0)
        l = (1.0 - cw) / 2.0
        t = (1.0 - ch) / 2.0
        self._crop = CropRect(l, t, l + cw, t + ch)
        self.crop_changed.emit(self._crop)
        self.update()

    def _on_frame(self, frame: QVideoFrame):
        if frame.isValid():
            img = frame.toImage()
            if not img.isNull():
                self._pixmap = QPixmap.fromImage(
                    img.convertToFormat(QImage.Format.Format_RGB32)
                )
                self.update()

    def _video_bounds(self) -> QRectF:
        if not self._video_info:
            return QRectF(0, 0, self.width(), self.height())
        vw = float(self._video_info.display_width)
        vh = float(self._video_info.display_height)
        cw = float(self.width())
        ch = float(self.height())
        va = vw / vh
        ca = cw / ch
        if va > ca:
            h = cw / va
            return QRectF(0.0, (ch - h) / 2.0, cw, h)
        else:
            w = ch * va
            return QRectF((cw - w) / 2.0, 0.0, w, ch)

    # ── Paint ─────────────────────────────────────────────────────────────────

    def paintEvent(self, _event):
        p = QPainter(self)
        p.setRenderHint(QPainter.RenderHint.Antialiasing)
        p.setRenderHint(QPainter.RenderHint.SmoothPixmapTransform)

        p.fillRect(self.rect(), Qt.GlobalColor.black)

        if self._pixmap and not self._pixmap.isNull():
            vb = self._video_bounds()
            p.drawPixmap(
                QRect(int(vb.x()), int(vb.y()),
                      int(vb.width()), int(vb.height())),
                self._pixmap,
            )
            if self._video_info:
                self._paint_overlay(p, vb)

        p.end()

    def _paint_overlay(self, p: QPainter, vb: QRectF):
        cr = self._crop
        cL = vb.x() + cr.left   * vb.width()
        cT = vb.y() + cr.top    * vb.height()
        cR = vb.x() + cr.right  * vb.width()
        cB = vb.y() + cr.bottom * vb.height()
        cW = cR - cL;  cH = cB - cT

        # Dimming
        dim = QColor(0, 0, 0, 178)
        p.fillRect(QRectF(vb.x(), vb.y(),    vb.width(),   cT - vb.y()),  dim)
        p.fillRect(QRectF(vb.x(), cB,         vb.width(),   vb.bottom()-cB), dim)
        p.fillRect(QRectF(vb.x(), cT,         cL - vb.x(), cH),             dim)
        p.fillRect(QRectF(cR,      cT,         vb.right()-cR, cH),           dim)

        # Rule-of-thirds
        p.setPen(QPen(QColor(255, 255, 255, 55), 1.0))
        for i in (1, 2):
            p.drawLine(QPointF(cL + cW*i/3, cT), QPointF(cL + cW*i/3, cB))
            p.drawLine(QPointF(cL, cT + cH*i/3), QPointF(cR, cT + cH*i/3))

        # Border
        p.setPen(QPen(QColor(255, 255, 255, 200), 1.5))
        p.drawRect(QRectF(cL, cT, cW, cH))

        # Corner and edge handles
        HL  = 20.0
        mid = HL * 0.55
        hp  = QPen(QColor(255, 255, 255), 3.0,
                   Qt.PenStyle.SolidLine, Qt.PenCapStyle.RoundCap)
        p.setPen(hp)

        def seg(x1, y1, x2, y2):
            p.drawLine(QPointF(x1, y1), QPointF(x2, y2))

        # TL
        seg(cL, cT+HL, cL, cT); seg(cL, cT, cL+HL, cT)
        # TR
        seg(cR-HL, cT, cR, cT); seg(cR, cT, cR, cT+HL)
        # BL
        seg(cL, cB-HL, cL, cB); seg(cL, cB, cL+HL, cB)
        # BR
        seg(cR-HL, cB, cR, cB); seg(cR, cB, cR, cB-HL)
        # edges
        cx = cL + cW/2;  cy = cT + cH/2
        seg(cx-mid, cT, cx+mid, cT)
        seg(cx-mid, cB, cx+mid, cB)
        seg(cL, cy-mid, cL, cy+mid)
        seg(cR, cy-mid, cR, cy+mid)

        # Dimension badge (top-right of crop)
        if self._video_info:
            pw = int(cr.width  * self._video_info.display_width)  & ~1
            ph = int(cr.height * self._video_info.display_height) & ~1
            txt = f"{pw}×{ph}"
            fnt = QFont("Segoe UI", 9)
            fm  = QFontMetrics(fnt)
            tw  = fm.horizontalAdvance(txt)
            th  = fm.height()
            pad = 5
            bx  = cR - tw - pad*2 - 4
            by  = cT + 4
            p.fillRect(QRectF(bx, by, tw+pad*2, th+pad), QColor(0, 0, 0, 160))
            p.setFont(fnt)
            p.setPen(QColor(255, 255, 255))
            p.drawText(QPointF(bx+pad, by+th), txt)

        # MUTED badge (top-left of video bounds)
        fnt2 = QFont("Segoe UI", 9)
        fm2  = QFontMetrics(fnt2)
        mtxt = "\U0001f507 MUTED"
        mw   = fm2.horizontalAdvance(mtxt)
        mh   = fm2.height()
        pad  = 5
        p.fillRect(QRectF(vb.x()+6, vb.y()+6, mw+pad*2, mh+pad), QColor(0, 0, 0, 160))
        p.setFont(fnt2)
        p.setPen(QColor(0xEF, 0x44, 0x44))
        p.drawText(QPointF(vb.x()+6+pad, vb.y()+6+mh), mtxt)

    # ── Mouse / hit-testing ───────────────────────────────────────────────────

    def _hit(self, pos: QPointF) -> DragMode:
        vb = self._video_bounds()
        cr = self._crop
        cL = vb.x() + cr.left   * vb.width()
        cT = vb.y() + cr.top    * vb.height()
        cR = vb.x() + cr.right  * vb.width()
        cB = vb.y() + cr.bottom * vb.height()

        x, y = pos.x(), pos.y()
        h = _HIT_PX

        def near(a, b): return abs(a-b) < h
        def in_x():     return (cL-h) <= x <= (cR+h)
        def in_y():     return (cT-h) <= y <= (cB+h)

        if near(x, cL) and near(y, cT): return DragMode.TL
        if near(x, cR) and near(y, cT): return DragMode.TR
        if near(x, cL) and near(y, cB): return DragMode.BL
        if near(x, cR) and near(y, cB): return DragMode.BR
        if near(y, cT) and in_x():      return DragMode.T
        if near(y, cB) and in_x():      return DragMode.B
        if near(x, cL) and in_y():      return DragMode.L
        if near(x, cR) and in_y():      return DragMode.R
        if cL <= x <= cR and cT <= y <= cB: return DragMode.MOVE
        return DragMode.NONE

    def mousePressEvent(self, ev):
        if ev.button() == Qt.MouseButton.LeftButton:
            mode = self._hit(ev.position())
            if mode != DragMode.NONE:
                self._drag_mode  = mode
                self._drag_start = ev.position()
                self._drag_crop0 = self._crop.copy()

    def mouseMoveEvent(self, ev):
        if ev.buttons() == Qt.MouseButton.NoButton:
            self.setCursor(QCursor(_CURSORS.get(self._hit(ev.position()),
                                                Qt.CursorShape.ArrowCursor)))
            return
        if self._drag_mode == DragMode.NONE or not self._drag_start:
            return
        vb = self._video_bounds()
        if vb.width() == 0 or vb.height() == 0:
            return
        delta = ev.position() - self._drag_start
        dx = delta.x() / vb.width()
        dy = delta.y() / vb.height()
        new_crop = self._apply_drag(self._drag_mode, self._drag_crop0, dx, dy)
        self._crop = new_crop.clamped()
        self.crop_changed.emit(self._crop)
        self.update()

    def mouseReleaseEvent(self, ev):
        self._drag_mode  = DragMode.NONE
        self._drag_start = None
        self._drag_crop0 = None

    def _apply_drag(self, mode: DragMode, c: CropRect, dx: float, dy: float) -> CropRect:
        asp = self._aspect
        MIN = _MIN_CROP

        if mode == DragMode.MOVE:
            w, h = c.width, c.height
            l = max(0.0, min(c.left + dx, 1.0 - w))
            t = max(0.0, min(c.top  + dy, 1.0 - h))
            return CropRect(l, t, l+w, t+h)

        def corner(dw, fix_l, fix_t):
            fx = c.left if fix_l else c.right
            fy = c.top  if fix_t else c.bottom
            nw = max(MIN, c.width + dw)
            nh = nw / asp
            mw = (1.0-fx) if fix_l else fx
            mh = (1.0-fy) if fix_t else fy
            if nw > mw: nw = mw; nh = nw / asp
            if nh > mh: nh = mh; nw = nh * asp
            l = fx if fix_l else fx - nw
            t = fy if fix_t else fy - nh
            return CropRect(l, t, l+nw, t+nh)

        def edge_v(dh, fix_t):
            fy = c.top if fix_t else c.bottom
            nh = max(MIN, c.height + dh)
            nw = nh * asp
            mh = (1.0-fy) if fix_t else fy
            if nh > mh: nh = mh; nw = nh * asp
            if nw > 1.0: nw = 1.0; nh = nw / asp
            cx = (c.left + c.right) / 2.0
            l  = max(0.0, min(cx - nw/2.0, 1.0-nw))
            t  = fy if fix_t else fy - nh
            return CropRect(l, t, l+nw, t+nh)

        def edge_h(dw, fix_l):
            fx = c.left if fix_l else c.right
            nw = max(MIN, c.width + dw)
            nh = nw / asp
            mw = (1.0-fx) if fix_l else fx
            if nw > mw: nw = mw; nh = nw / asp
            if nh > 1.0: nh = 1.0; nw = nh * asp
            l  = fx if fix_l else fx - nw
            cy = (c.top + c.bottom) / 2.0
            t  = max(0.0, min(cy - nh/2.0, 1.0-nh))
            return CropRect(l, t, l+nw, t+nh)

        if mode == DragMode.BR: return corner( dx, True,  True)
        if mode == DragMode.BL: return corner(-dx, False, True)
        if mode == DragMode.TR: return corner( dx, True,  False)
        if mode == DragMode.TL: return corner(-dx, False, False)
        if mode == DragMode.R:  return edge_h( dx, True)
        if mode == DragMode.L:  return edge_h(-dx, False)
        if mode == DragMode.B:  return edge_v( dy, True)
        if mode == DragMode.T:  return edge_v(-dy, False)
        return c
