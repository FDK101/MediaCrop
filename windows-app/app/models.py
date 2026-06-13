from dataclasses import dataclass
from enum import Enum, auto


class DragMode(Enum):
    NONE = auto()
    MOVE = auto()
    TL = auto(); TR = auto(); BL = auto(); BR = auto()
    T  = auto(); B  = auto(); L  = auto(); R  = auto()


@dataclass
class VideoInfo:
    path: str
    raw_width: int
    raw_height: int
    duration_ms: int
    rotation: int = 0

    @property
    def display_width(self) -> int:
        return self.raw_height if self.rotation in (90, 270) else self.raw_width

    @property
    def display_height(self) -> int:
        return self.raw_width if self.rotation in (90, 270) else self.raw_height

    @property
    def display_aspect(self) -> float:
        return self.display_width / self.display_height


@dataclass
class CropRect:
    left:   float = 0.0
    top:    float = 0.0
    right:  float = 1.0
    bottom: float = 1.0

    @property
    def width(self)  -> float: return self.right  - self.left
    @property
    def height(self) -> float: return self.bottom - self.top
    @property
    def aspect_ratio(self) -> float:
        return self.width / self.height if self.height > 0 else 1.0

    def clamped(self) -> "CropRect":
        w = self.right  - self.left
        h = self.bottom - self.top
        l = max(0.0, min(self.left, 1.0 - w))
        t = max(0.0, min(self.top,  1.0 - h))
        return CropRect(l, t, l + w, t + h)

    def copy(self) -> "CropRect":
        return CropRect(self.left, self.top, self.right, self.bottom)
