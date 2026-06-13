using System.Globalization;
using System.Windows;
using System.Windows.Input;
using System.Windows.Media;
using MediaCrop.Models;

namespace MediaCrop.Controls;

/// <summary>Custom FrameworkElement that draws the crop overlay on top of the video.</summary>
public class CropOverlayElement : FrameworkElement
{
    // ── Colours matching Python theme ────────────────────────────────────────
    static readonly Color DimBg      = Color.FromArgb(178, 0, 0, 0);
    static readonly Color GridLine   = Color.FromArgb(55, 255, 255, 255);
    static readonly Color BorderCol  = Color.FromArgb(200, 255, 255, 255);
    static readonly Color HandleCol  = Colors.White;
    static readonly Color BadgeBg    = Color.FromArgb(160, 0, 0, 0);
    static readonly Color MutedCol   = Color.FromRgb(0xEF, 0x44, 0x44);

    const double HandleLen = 20.0;
    const double HandleMid = 11.0;
    const double HitPx     = 20.0;
    const double MinCrop   = 0.05;

    // ── State ────────────────────────────────────────────────────────────────
    private VideoInfo? _info;
    private CropRect   _crop    = CropRect.FullFrame;
    private double     _aspect  = 9.0 / 16.0;
    private DragMode   _drag    = DragMode.None;
    private Point      _dragOrigin;
    private CropRect   _dragCrop0;

    private static readonly Typeface BadgeTypeface =
        new(new FontFamily("Segoe UI"), FontStyles.Normal, FontWeights.Normal, FontStretches.Normal);

    public event Action<CropRect>? CropChanged;

    public CropOverlayElement()
    {
        // Transparent background makes the entire area hittable for mouse events
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void SetVideoInfo(VideoInfo info)
    {
        _info = info;
        InvalidateVisual();
    }

    public CropRect GetCrop() => _crop;

    public void SetAspect(double aspect)
    {
        _aspect = aspect;
        RecalculateCrop();
    }

    public void RecalculateCrop()
    {
        if (_info == null) return;
        double va = _info.DisplayAspect;
        double sa = _aspect;
        double cw, ch;
        if (sa <= va) { cw = sa / va; ch = 1.0; }
        else          { cw = 1.0;     ch = va / sa; }
        cw = Math.Min(cw, 1.0);
        ch = Math.Min(ch, 1.0);
        double l = (1.0 - cw) / 2.0, t = (1.0 - ch) / 2.0;
        _crop = new CropRect(l, t, l + cw, t + ch);
        CropChanged?.Invoke(_crop);
        InvalidateVisual();
    }

    // ── Hit testing ──────────────────────────────────────────────────────────

    protected override HitTestResult? HitTestCore(PointHitTestParameters p)
        => new PointHitTestResult(this, p.HitPoint);

    // ── Geometry ─────────────────────────────────────────────────────────────

    private Rect GetVideoBounds()
    {
        if (_info == null) return new Rect(0, 0, ActualWidth, ActualHeight);
        double vw = _info.DisplayWidth, vh = _info.DisplayHeight;
        double cw = ActualWidth, ch = ActualHeight;
        double va = vw / vh, ca = cw / ch;
        if (va > ca) { double h = cw / va; return new Rect(0, (ch - h) / 2, cw, h); }
        else         { double w = ch * va; return new Rect((cw - w) / 2, 0, w, ch); }
    }

    private (double cL, double cT, double cR, double cB) CropPixels(Rect vb)
    {
        return (
            vb.X + _crop.Left   * vb.Width,
            vb.Y + _crop.Top    * vb.Height,
            vb.X + _crop.Right  * vb.Width,
            vb.Y + _crop.Bottom * vb.Height
        );
    }

    // ── Render ────────────────────────────────────────────────────────────────

    protected override void OnRender(DrawingContext dc)
    {
        // Fully transparent background (keeps the element hittable)
        dc.DrawRectangle(Brushes.Transparent, null, new Rect(0, 0, ActualWidth, ActualHeight));

        if (_info == null) return;

        var vb = GetVideoBounds();
        var (cL, cT, cR, cB) = CropPixels(vb);
        double cW = cR - cL, cH = cB - cT;

        var dim = new SolidColorBrush(DimBg);
        dc.DrawRectangle(dim, null, new Rect(vb.X, vb.Y,       vb.Width, cT - vb.Y));
        dc.DrawRectangle(dim, null, new Rect(vb.X, cB,         vb.Width, vb.Bottom - cB));
        dc.DrawRectangle(dim, null, new Rect(vb.X, cT,         cL - vb.X, cH));
        dc.DrawRectangle(dim, null, new Rect(cR,   cT,         vb.Right - cR, cH));

        // Rule-of-thirds grid
        var gp = new Pen(new SolidColorBrush(GridLine), 1.0);
        for (int i = 1; i <= 2; i++)
        {
            dc.DrawLine(gp, new Point(cL + cW * i / 3.0, cT), new Point(cL + cW * i / 3.0, cB));
            dc.DrawLine(gp, new Point(cL, cT + cH * i / 3.0), new Point(cR, cT + cH * i / 3.0));
        }

        // Crop border
        dc.DrawRectangle(null, new Pen(new SolidColorBrush(BorderCol), 1.5),
            new Rect(cL, cT, cW, cH));

        // L-shaped corner and mid-edge handles
        var hp = new Pen(new SolidColorBrush(HandleCol), 3.0)
            { StartLineCap = PenLineCap.Round, EndLineCap = PenLineCap.Round };
        double HL = HandleLen, mid = HandleMid;
        double cx = cL + cW / 2, cy = cT + cH / 2;

        void Seg(double x1, double y1, double x2, double y2)
            => dc.DrawLine(hp, new Point(x1, y1), new Point(x2, y2));

        Seg(cL,      cT + HL, cL,      cT);      Seg(cL, cT, cL + HL, cT);       // TL
        Seg(cR - HL, cT,      cR,      cT);      Seg(cR, cT, cR,      cT + HL);  // TR
        Seg(cL,      cB - HL, cL,      cB);      Seg(cL, cB, cL + HL, cB);       // BL
        Seg(cR - HL, cB,      cR,      cB);      Seg(cR, cB, cR,      cB - HL);  // BR
        Seg(cx - mid, cT, cx + mid, cT);   Seg(cx - mid, cB, cx + mid, cB);      // T/B
        Seg(cL, cy - mid, cL, cy + mid);   Seg(cR, cy - mid, cR, cy + mid);      // L/R

        double dpi = VisualTreeHelper.GetDpi(this).PixelsPerDip;

        // Dimension badge (top-right of crop)
        int pw = (int)(_crop.Width  * _info.DisplayWidth)  & ~1;
        int ph = (int)(_crop.Height * _info.DisplayHeight) & ~1;
        DrawBadge(dc, $"{pw}×{ph}", cR - 4, cT + 4, alignRight: true, Colors.White, dpi);

        // MUTED badge (top-left of video area)
        DrawBadge(dc, "🔇 MUTED", vb.X + 6, vb.Y + 6, alignRight: false, MutedCol, dpi);
    }

    private static void DrawBadge(DrawingContext dc, string text, double x, double y,
        bool alignRight, Color textColor, double dpi)
    {
        var ft = new FormattedText(text, CultureInfo.CurrentCulture, FlowDirection.LeftToRight,
            BadgeTypeface, 11.0, new SolidColorBrush(textColor), dpi);
        const double pad = 5;
        double bx = alignRight ? x - ft.Width - pad * 2 : x;
        dc.DrawRectangle(new SolidColorBrush(BadgeBg), null,
            new Rect(bx, y, ft.Width + pad * 2, ft.Height + pad));
        dc.DrawText(ft, new Point(bx + pad, y + pad / 2.0));
    }

    // ── Mouse interaction ─────────────────────────────────────────────────────

    private static readonly Dictionary<DragMode, Cursor> DragCursors = new()
    {
        { DragMode.Move, Cursors.SizeAll  },
        { DragMode.TL,   Cursors.SizeNWSE },
        { DragMode.BR,   Cursors.SizeNWSE },
        { DragMode.TR,   Cursors.SizeNESW },
        { DragMode.BL,   Cursors.SizeNESW },
        { DragMode.T,    Cursors.SizeNS   },
        { DragMode.B,    Cursors.SizeNS   },
        { DragMode.L,    Cursors.SizeWE   },
        { DragMode.R,    Cursors.SizeWE   },
    };

    private DragMode HitTest(Point pos)
    {
        var vb = GetVideoBounds();
        var (cL, cT, cR, cB) = CropPixels(vb);
        double x = pos.X, y = pos.Y, h = HitPx;

        bool NX(double a) => Math.Abs(x - a) < h;
        bool NY(double a) => Math.Abs(y - a) < h;
        bool InX() => x >= cL - h && x <= cR + h;
        bool InY() => y >= cT - h && y <= cB + h;

        if (NX(cL) && NY(cT)) return DragMode.TL;
        if (NX(cR) && NY(cT)) return DragMode.TR;
        if (NX(cL) && NY(cB)) return DragMode.BL;
        if (NX(cR) && NY(cB)) return DragMode.BR;
        if (NY(cT) && InX())  return DragMode.T;
        if (NY(cB) && InX())  return DragMode.B;
        if (NX(cL) && InY())  return DragMode.L;
        if (NX(cR) && InY())  return DragMode.R;
        if (x >= cL && x <= cR && y >= cT && y <= cB) return DragMode.Move;
        return DragMode.None;
    }

    protected override void OnMouseMove(MouseEventArgs e)
    {
        var pos = e.GetPosition(this);
        if (e.LeftButton == MouseButtonState.Released)
        {
            var mode = HitTest(pos);
            Cursor = DragCursors.GetValueOrDefault(mode, Cursors.Arrow);
            return;
        }
        if (_drag == DragMode.None) return;

        var vb = GetVideoBounds();
        if (vb.Width < 1 || vb.Height < 1) return;

        double dx = (pos.X - _dragOrigin.X) / vb.Width;
        double dy = (pos.Y - _dragOrigin.Y) / vb.Height;
        _crop = ApplyDrag(_drag, _dragCrop0, dx, dy).Clamped();
        CropChanged?.Invoke(_crop);
        InvalidateVisual();
    }

    protected override void OnMouseLeftButtonDown(MouseButtonEventArgs e)
    {
        var pos = e.GetPosition(this);
        var mode = HitTest(pos);
        if (mode != DragMode.None)
        {
            _drag      = mode;
            _dragOrigin = pos;
            _dragCrop0 = _crop.Copy();
            CaptureMouse();
            e.Handled = true;
        }
    }

    protected override void OnMouseLeftButtonUp(MouseButtonEventArgs e)
    {
        _drag = DragMode.None;
        ReleaseMouseCapture();
    }

    private CropRect ApplyDrag(DragMode mode, CropRect c, double dx, double dy)
    {
        double asp = _aspect;
        const double MIN = MinCrop;

        if (mode == DragMode.Move)
        {
            double w = c.Width, h = c.Height;
            double l = Math.Max(0, Math.Min(c.Left + dx, 1 - w));
            double t = Math.Max(0, Math.Min(c.Top  + dy, 1 - h));
            return new CropRect(l, t, l + w, t + h);
        }

        CropRect Corner(double dw, bool fixL, bool fixT)
        {
            double fx = fixL ? c.Left : c.Right;
            double fy = fixT ? c.Top  : c.Bottom;
            double nw = Math.Max(MIN, c.Width + dw);
            double nh = nw / asp;
            double mw = fixL ? (1 - fx) : fx;
            double mh = fixT ? (1 - fy) : fy;
            if (nw > mw) { nw = mw; nh = nw / asp; }
            if (nh > mh) { nh = mh; nw = nh * asp; }
            double l = fixL ? fx : fx - nw;
            double t = fixT ? fy : fy - nh;
            return new CropRect(l, t, l + nw, t + nh);
        }

        CropRect EdgeV(double dh, bool fixT)
        {
            double fy = fixT ? c.Top : c.Bottom;
            double nh = Math.Max(MIN, c.Height + dh);
            double nw = nh * asp;
            double mh = fixT ? (1 - fy) : fy;
            if (nh > mh) { nh = mh; nw = nh * asp; }
            if (nw > 1)  { nw = 1;  nh = nw / asp; }
            double mid = (c.Left + c.Right) / 2;
            double l   = Math.Max(0, Math.Min(mid - nw / 2, 1 - nw));
            double t   = fixT ? fy : fy - nh;
            return new CropRect(l, t, l + nw, t + nh);
        }

        CropRect EdgeH(double dw, bool fixL)
        {
            double fx = fixL ? c.Left : c.Right;
            double nw = Math.Max(MIN, c.Width + dw);
            double nh = nw / asp;
            double mw = fixL ? (1 - fx) : fx;
            if (nw > mw) { nw = mw; nh = nw / asp; }
            if (nh > 1)  { nh = 1;  nw = nh * asp; }
            double l   = fixL ? fx : fx - nw;
            double mid = (c.Top + c.Bottom) / 2;
            double t   = Math.Max(0, Math.Min(mid - nh / 2, 1 - nh));
            return new CropRect(l, t, l + nw, t + nh);
        }

        return mode switch
        {
            DragMode.BR => Corner( dx, true,  true),
            DragMode.BL => Corner(-dx, false, true),
            DragMode.TR => Corner( dx, true,  false),
            DragMode.TL => Corner(-dx, false, false),
            DragMode.R  => EdgeH( dx, true),
            DragMode.L  => EdgeH(-dx, false),
            DragMode.B  => EdgeV( dy, true),
            DragMode.T  => EdgeV(-dy, false),
            _           => c,
        };
    }
}
