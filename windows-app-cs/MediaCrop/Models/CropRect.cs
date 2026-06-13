namespace MediaCrop.Models;

public struct CropRect
{
    public double Left   { get; set; }
    public double Top    { get; set; }
    public double Right  { get; set; }
    public double Bottom { get; set; }

    public CropRect(double left, double top, double right, double bottom)
    {
        Left = left; Top = top; Right = right; Bottom = bottom;
    }

    public double Width  => Right  - Left;
    public double Height => Bottom - Top;

    public CropRect Clamped()
    {
        double w = Width, h = Height;
        double l = Math.Max(0, Math.Min(Left, 1 - w));
        double t = Math.Max(0, Math.Min(Top,  1 - h));
        return new CropRect(l, t, l + w, t + h);
    }

    public CropRect Copy() => new(Left, Top, Right, Bottom);

    public static CropRect FullFrame => new(0, 0, 1, 1);
}
