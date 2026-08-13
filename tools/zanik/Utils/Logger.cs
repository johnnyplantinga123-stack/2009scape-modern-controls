using Discord;

namespace ZanikNet.Utils;

public static class Logger
{
    public static Task Log(LogMessage msg)
    {
        Console.WriteLine(msg);
        return Task.CompletedTask;
    }
}