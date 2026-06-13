PRIMARY         = "#6366F1"
PRIMARY_HOVER   = "#818CF8"
PRIMARY_PRESS   = "#4F46E5"
SECONDARY       = "#06B6D4"
BG              = "#0F0F0F"
SURFACE         = "#1A1A1A"
SURFACE_VAR     = "#262626"
ON_BG           = "#F5F5F5"
ON_SURFACE      = "#E5E5E5"
ON_SURFACE_VAR  = "#9CA3AF"
ERROR           = "#EF4444"
SUCCESS         = "#22C55E"
TRACK_ACTIVE    = "#6366F1"
TRACK_INACTIVE  = "#374151"
THUMB           = "#6366F1"

APP_QSS = f"""
* {{
    background-color: {BG};
    color: {ON_BG};
    font-family: "Segoe UI";
    font-size: 13px;
    border: none;
    outline: none;
}}
QMainWindow, QDialog {{
    background-color: {BG};
}}
QWidget {{
    background-color: {BG};
    color: {ON_BG};
}}
QLabel {{
    background: transparent;
    color: {ON_BG};
}}
QLabel#subtitle {{
    color: {ON_SURFACE_VAR};
    font-size: 12px;
}}
QPushButton {{
    background-color: {SURFACE_VAR};
    color: {ON_BG};
    border-radius: 8px;
    padding: 6px 14px;
    min-height: 28px;
    font-size: 13px;
}}
QPushButton:hover  {{ background-color: #323232; }}
QPushButton:pressed {{ background-color: #1e1e1e; }}
QPushButton[role="primary"] {{
    background-color: {PRIMARY};
    color: white;
    font-weight: bold;
    font-size: 14px;
    min-height: 44px;
    border-radius: 12px;
}}
QPushButton[role="primary"]:hover  {{ background-color: {PRIMARY_HOVER}; }}
QPushButton[role="primary"]:pressed {{ background-color: {PRIMARY_PRESS}; }}
QPushButton[role="primary"]:disabled {{ background-color: #3a3a3a; color: {ON_SURFACE_VAR}; }}
QPushButton[role="preset"] {{
    background-color: {SURFACE_VAR};
    color: {ON_SURFACE_VAR};
    border-radius: 8px;
    padding: 5px 12px;
    font-size: 12px;
}}
QPushButton[role="preset"][active="true"] {{
    background-color: {PRIMARY};
    color: white;
}}
QPushButton[role="preset"]:hover  {{ background-color: #323232; }}
QPushButton[role="adjust"] {{
    background-color: transparent;
    color: {PRIMARY};
    border-radius: 4px;
    padding: 2px 5px;
    min-height: 22px;
    font-size: 11px;
}}
QPushButton[role="adjust"]:hover  {{ background-color: #1e1e2e; }}
QPushButton[role="icon"] {{
    background-color: transparent;
    color: {ON_SURFACE};
    border-radius: 20px;
    min-width: 40px;
    min-height: 40px;
    font-size: 18px;
}}
QPushButton[role="icon"]:hover  {{ background-color: {SURFACE_VAR}; }}
QPushButton[role="play"] {{
    background-color: {PRIMARY};
    color: white;
    border-radius: 24px;
    min-width: 48px;
    min-height: 48px;
    font-size: 20px;
    font-weight: bold;
}}
QPushButton[role="play"]:hover  {{ background-color: {PRIMARY_HOVER}; }}
QPushButton[role="back"] {{
    background-color: transparent;
    color: {ON_BG};
    border-radius: 8px;
    padding: 4px 10px;
    font-size: 14px;
}}
QPushButton[role="back"]:hover {{ background-color: {SURFACE_VAR}; }}
QPushButton[role="adb"] {{
    background-color: #1a2e1a;
    color: {SUCCESS};
    border-radius: 8px;
    padding: 5px 12px;
    font-size: 12px;
}}
QPushButton[role="adb"]:hover {{ background-color: #223822; }}
QSlider::groove:horizontal {{
    height: 6px;
    background-color: {TRACK_INACTIVE};
    border-radius: 3px;
}}
QSlider::handle:horizontal {{
    background-color: {THUMB};
    width: 16px;
    height: 16px;
    margin: -5px 0;
    border-radius: 8px;
}}
QSlider::sub-page:horizontal {{
    background-color: {TRACK_ACTIVE};
    border-radius: 3px;
}}
QScrollArea, QScrollArea > QWidget > QWidget {{
    background-color: {BG};
}}
QScrollBar:vertical {{
    background: {SURFACE};
    width: 6px;
    border-radius: 3px;
}}
QScrollBar::handle:vertical {{
    background: {SURFACE_VAR};
    border-radius: 3px;
    min-height: 20px;
}}
QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical {{ height: 0; }}
QFrame#divider {{
    background-color: {SURFACE_VAR};
    max-height: 1px;
    min-height: 1px;
}}
QSpinBox {{
    background-color: {SURFACE_VAR};
    color: {ON_BG};
    border-radius: 6px;
    padding: 4px 8px;
    min-height: 28px;
}}
QSpinBox::up-button, QSpinBox::down-button {{
    background: transparent;
    width: 16px;
}}
QMessageBox {{
    background-color: {SURFACE};
}}
QMessageBox QLabel {{ color: {ON_BG}; }}
QMessageBox QPushButton {{
    min-width: 80px;
    background-color: {PRIMARY};
    color: white;
    border-radius: 6px;
    padding: 6px 14px;
}}
"""
