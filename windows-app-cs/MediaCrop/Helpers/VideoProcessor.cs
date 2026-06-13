using System.Diagnostics;
using System.IO;
using System.Text.Json;
using MediaCrop.Models;

namespace MediaCrop.Helpers;

public static class VideoProcessor
{
    public static VideoInfo ProbeVideo(string path)
    {
        var psi = new ProcessStartInfo(
            "ffprobe",
            $"-v quiet -print_format json -show_streams -show_format \"{path}\"")
        {
            RedirectStandardOutput = true,
            RedirectStandardError  = true,
            UseShellExecute        = false,
            CreateNoWindow         = true,
        };

        Process p;
        try { p = Process.Start(psi)!; }
        catch
        {
            throw new Exception(
                "FFmpeg / ffprobe not found in PATH.\n" +
                "Download from https://ffmpeg.org/download.html and add the bin\\ folder to PATH.");
        }

        string jsonStr = p.StandardOutput.ReadToEnd();
        p.WaitForExit(30000);

        JsonElement root;
        try { root = JsonDocument.Parse(jsonStr).RootElement; }
        catch { throw new Exception("ffprobe returned unexpected output."); }

        var stream = root.GetProperty("streams").EnumerateArray()
            .FirstOrDefault(s => s.TryGetProperty("codec_type", out var ct)
                                 && ct.GetString() == "video");

        int rawW  = stream.TryGetProperty("width",  out var w) ? w.GetInt32()  : 0;
        int rawH  = stream.TryGetProperty("height", out var h) ? h.GetInt32()  : 0;
        double durS = 0;
        if (root.TryGetProperty("format", out var fmt) &&
            fmt.TryGetProperty("duration", out var d))
            double.TryParse(d.GetString(), System.Globalization.NumberStyles.Float,
                System.Globalization.CultureInfo.InvariantCulture, out durS);

        int rotation = 0;
        if (stream.TryGetProperty("side_data_list", out var sdl))
        {
            foreach (var sd in sdl.EnumerateArray())
            {
                if (sd.TryGetProperty("side_data_type", out var sdt) &&
                    sdt.GetString() == "Display Matrix" &&
                    sd.TryGetProperty("rotation", out var rot))
                {
                    rotation = ((-rot.GetInt32()) % 360 + 360) % 360;
                    break;
                }
            }
        }
        if (rotation == 0 && stream.TryGetProperty("tags", out var tags) &&
            tags.TryGetProperty("rotate", out var r))
        {
            int.TryParse(r.GetString(), out rotation);
            rotation = (rotation % 360 + 360) % 360;
        }

        return new VideoInfo
        {
            Path       = path,
            RawWidth   = rawW,
            RawHeight  = rawH,
            DurationMs = (long)(durS * 1000),
            Rotation   = rotation,
        };
    }

    public static async Task ExportAsync(
        VideoInfo info,
        CropRect  crop,
        long      startMs,
        long      endMs,
        string    outputPath,
        IProgress<double> progress,
        CancellationToken ct)
    {
        long dur = endMs - startMs;
        int dw = info.DisplayWidth, dh = info.DisplayHeight;

        int cx = (int)(crop.Left   * dw);
        int cy = (int)(crop.Top    * dh);
        int cw = (int)(crop.Width  * dw); cw -= cw % 2; cw = Math.Max(2, cw);
        int ch = (int)(crop.Height * dh); ch -= ch % 2; ch = Math.Max(2, ch);

        string cropFilter = $"crop={cw}:{ch}:{cx}:{cy}";
        string vf = info.Rotation switch
        {
            90  => $"transpose=1,{cropFilter}",
            180 => $"hflip,vflip,{cropFilter}",
            270 => $"transpose=2,{cropFilter}",
            _   => cropFilter,
        };

        string args =
            $"-y -ss {startMs / 1000.0:F3} -i \"{info.Path}\" -t {dur / 1000.0:F3} " +
            $"-vf \"{vf}\" -c:v libx264 -crf 18 -preset fast -movflags +faststart " +
            $"-an -metadata:s:v:0 rotate=0 -progress pipe:1 \"{outputPath}\"";

        var psi = new ProcessStartInfo("ffmpeg", args)
        {
            RedirectStandardOutput = true,
            RedirectStandardError  = true,
            UseShellExecute        = false,
            CreateNoWindow         = true,
        };

        Process proc;
        try { proc = Process.Start(psi)!; }
        catch { throw new Exception("FFmpeg not found in PATH."); }

        string? line;
        while ((line = await proc.StandardOutput.ReadLineAsync(ct)) != null)
        {
            ct.ThrowIfCancellationRequested();
            if (line.StartsWith("out_time_us=") &&
                long.TryParse(line["out_time_us=".Length..], out long us) && dur > 0)
            {
                progress.Report(Math.Min((double)us / (dur * 1000.0), 0.99));
            }
        }

        await proc.WaitForExitAsync(ct);
        if (proc.ExitCode != 0)
        {
            string err = await proc.StandardError.ReadToEndAsync(ct);
            int cut = Math.Max(0, err.Length - 600);
            throw new Exception($"FFmpeg failed (exit {proc.ExitCode}):\n{err[cut..]}");
        }

        progress.Report(1.0);
    }
}
