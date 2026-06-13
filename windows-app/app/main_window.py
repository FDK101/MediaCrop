from PyQt6.QtWidgets import QMainWindow, QStackedWidget
from PyQt6.QtCore import QSize

from .home_screen   import HomeScreen
from .editor_screen import EditorScreen
from .models        import VideoInfo


class MainWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("Media Crop")
        self.setMinimumSize(QSize(800, 680))
        self.resize(1000, 780)

        self._stack = QStackedWidget()
        self.setCentralWidget(self._stack)

        self._home   = HomeScreen()
        self._editor = EditorScreen()

        self._stack.addWidget(self._home)    # index 0
        self._stack.addWidget(self._editor)  # index 1

        self._home.video_selected.connect(self._open_editor)
        self._editor.back_requested.connect(self._go_home)

    def _open_editor(self, info: VideoInfo):
        self._editor.load_video(info)
        self._stack.setCurrentIndex(1)

    def _go_home(self):
        self._stack.setCurrentIndex(0)
