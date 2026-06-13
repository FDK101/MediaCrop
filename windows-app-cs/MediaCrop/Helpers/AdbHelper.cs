using System.ComponentModel;
using System.Diagnostics;
using System.Text.RegularExpressions;

namespace MediaCrop.Helpers;

public static class AdbHelper
{
    public static (int Width, int Height) GetDeviceScreenSize()
    {
        string devicesOut;
        try { devicesOut = RunAdb("devices"); }
        catch (Win32Exception)
        {
            throw new Exception(
                "ADB not found in PATH.\n" +
                "Install Android Platform Tools and add the folder to your system PATH.");
        }

        bool hasDevice = devicesOut.Split('\n')
            .Skip(1)
            .Any(l => l.Contains("\tdevice"));
        if (!hasDevice)
            throw new Exception(
                "No Android device detected.\n" +
                "Connect a phone via USB with USB Debugging enabled, then try again.");

        string sizeOut = RunAdb("shell wm size");
        var m = Regex.Match(sizeOut, @"(?:Override|Physical) size:\s*(\d+)x(\d+)");
        if (!m.Success) m = Regex.Match(sizeOut, @"(\d+)x(\d+)");
        if (!m.Success)
            throw new Exception($"Could not parse screen size.\nADB output: {sizeOut}");

        return (int.Parse(m.Groups[1].Value), int.Parse(m.Groups[2].Value));
    }

    private static string RunAdb(string args)
    {
        var psi = new ProcessStartInfo("adb", args)
        {
            RedirectStandardOutput = true,
            RedirectStandardError  = true,
            UseShellExecute        = false,
            CreateNoWindow         = true,
        };
        using var p = Process.Start(psi)!;
        string output = p.StandardOutput.ReadToEnd();
        p.WaitForExit(5000);
        return output;
    }
}
