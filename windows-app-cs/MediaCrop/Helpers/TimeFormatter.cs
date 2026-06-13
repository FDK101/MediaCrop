namespace MediaCrop.Helpers;

public static class TimeFormatter
{
    public static string Format(long ms)
    {
        ms = Math.Max(0, ms);
        long mins   = ms / 60000;
        long secs   = (ms % 60000) / 1000;
        long millis = ms % 1000;
        return $"{mins:D2}:{secs:D2}.{millis:D3}";
    }
}
