import subprocess
import json
import re
from pathlib import Path
from PyQt6.QtCore import QThread, pyqtSignal
from .models import VideoInfo, CropRect


def probe_video(path: str) -> VideoInfo:
    """Run ffprobe and return VideoInfo.  Raises RuntimeError if ffprobe not found."""
    cmd = [
        "ffprobe", "-v", "quiet",
        "-print_format", "json",
        "-show_streams", "-show_format",
        path,
    ]
    try:
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
    except FileNotFoundError:
        raise RuntimeError(
            "FFmpeg / ffprobe not found in PATH.\n"
            "Download FFmpeg from https://ffmpeg.org/download.html and add the bin folder to PATH."
        )

    try:
        data = json.loads(result.stdout)
    except json.JSONDecodeError:
        raise RuntimeError(f"ffprobe returned unexpected output:\n{result.stderr[:400]}")

    stream = next(
        (s for s in data.get("streams", []) if s.get("codec_type") == "video"),
        None,
    )
    if stream is None:
        raise ValueError("No video stream found in this file.")

    width  = int(stream.get("width",  0))
    height = int(stream.get("height", 0))
    dur_s  = float(data.get("format", {}).get("duration", 0))

    # Rotation — stored as side_data Display Matrix or as a tags entry
    rotation = 0
    for sd in stream.get("side_data_list", []):
        if sd.get("side_data_type") == "Display Matrix":
            try:
                rotation = (-int(sd.get("rotation", 0))) % 360
            except (TypeError, ValueError):
                pass
            break
    if rotation == 0:
        try:
            rotation = int(stream.get("tags", {}).get("rotate", 0)) % 360
        except (TypeError, ValueError):
            pass

    return VideoInfo(
        path=path,
        raw_width=width,
        raw_height=height,
        duration_ms=int(dur_s * 1000),
        rotation=rotation,
    )


class ExportThread(QThread):
    progress  = pyqtSignal(float)   # 0.0–1.0
    completed = pyqtSignal(str)     # output file path
    failed    = pyqtSignal(str)     # error message

    def __init__(
        self,
        video_info: VideoInfo,
        crop_rect:  CropRect,
        start_ms:   int,
        end_ms:     int,
        output_path: str,
        parent=None,
    ):
        super().__init__(parent)
        self._vi   = video_info
        self._crop = crop_rect
        self._start_ms  = start_ms
        self._end_ms    = end_ms
        self._output    = output_path

    def run(self):
        try:
            self._export()
        except Exception as exc:
            self.failed.emit(str(exc))

    def _export(self):
        vi   = self._vi
        crop = self._crop
        dur  = self._end_ms - self._start_ms

        dw = vi.display_width
        dh = vi.display_height

        # Crop region in display-space pixels, rounded to even
        cx = int(crop.left   * dw)
        cy = int(crop.top    * dh)
        cw = int(crop.width  * dw); cw -= cw % 2
        ch = int(crop.height * dh); ch -= ch % 2
        cw = max(2, cw); ch = max(2, ch)

        # Build video filter: rotate first so crop operates in display coords
        crop_f = f"crop={cw}:{ch}:{cx}:{cy}"
        rot = vi.rotation
        if   rot == 90:  vf = f"transpose=1,{crop_f}"
        elif rot == 180: vf = f"hflip,vflip,{crop_f}"
        elif rot == 270: vf = f"transpose=2,{crop_f}"
        else:            vf = crop_f

        cmd = [
            "ffmpeg", "-y",
            "-ss", f"{self._start_ms / 1000:.3f}",
            "-i", vi.path,
            "-t",  f"{dur / 1000:.3f}",
            "-vf", vf,
            "-c:v", "libx264",
            "-crf", "18",
            "-preset", "fast",
            "-movflags", "+faststart",
            "-an",                          # always muted — by design
            "-metadata:s:v:0", "rotate=0",  # clear rotation tag (already applied)
            "-progress", "pipe:1",
            self._output,
        ]

        try:
            proc = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
            )
        except FileNotFoundError:
            raise RuntimeError(
                "FFmpeg not found in PATH.\n"
                "Download FFmpeg from https://ffmpeg.org/download.html and add the bin folder to PATH."
            )

        for line in proc.stdout:
            line = line.strip()
            if line.startswith("out_time_us="):
                try:
                    us = int(line.split("=", 1)[1])
                    if dur > 0:
                        self.progress.emit(min(us / (dur * 1000), 0.99))
                except (ValueError, ZeroDivisionError):
                    pass

        proc.wait()
        if proc.returncode != 0:
            stderr = proc.stderr.read() if proc.stderr else ""
            raise RuntimeError(f"FFmpeg failed (exit {proc.returncode}):\n{stderr[-600:]}")

        self.progress.emit(1.0)
        self.completed.emit(self._output)
