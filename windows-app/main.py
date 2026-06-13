import sys
from PyQt6.QtWidgets import QApplication
from PyQt6.QtCore import Qt
from app.theme       import APP_QSS
from app.main_window import MainWindow


def main():
    # High-DPI support
    QApplication.setHighDpiScaleFactorRoundingPolicy(
        Qt.HighDpiScaleFactorRoundingPolicy.PassThrough
    )
    app = QApplication(sys.argv)
    app.setApplicationName("Media Crop")
    app.setOrganizationName("MediaCrop")
    app.setStyleSheet(APP_QSS)

    window = MainWindow()
    window.show()

    sys.exit(app.exec())


if __name__ == "__main__":
    main()
