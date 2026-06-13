namespace MediaCrop.Models;

public class VideoInfo
{
    public string Path       { get; init; } = "";
    public int    RawWidth   { get; init; }
    public int    RawHeight  { get; init; }
    public long   DurationMs { get; init; }
    public int    Rotation   { get; init; }

    public int    DisplayWidth  => (Rotation == 90 || Rotation == 270) ? RawHeight : RawWidth;
    public int    DisplayHeight => (Rotation == 90 || Rotation == 270) ? RawWidth  : RawHeight;
    public double DisplayAspect => (double)DisplayWidth / DisplayHeight;
}
