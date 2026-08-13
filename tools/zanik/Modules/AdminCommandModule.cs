using Discord.Interactions;
using ZanikNet.Utils;

namespace ZanikNet.Modules;

public class AdminCommandModule : InteractionModuleBase
{
    [SlashCommand("restart", "Restarts the bot.")]
    [RequireUserPermission(Discord.GuildPermission.Administrator)]
    public async Task RestartCommand()
    {
        await RespondAsync("Restarting bot...");
        if (!string.IsNullOrEmpty(Vars.RestartFilePath))
        {
            await using var fs = File.Create(Vars.RestartFilePath);
        }
        Environment.Exit(0);
    }

    [SlashCommand("stop", "Stops the bot.")]
    [RequireUserPermission(Discord.GuildPermission.Administrator)]
    public async Task StopCommand()
    {
        await RespondAsync("Stopping bot...");
        Environment.Exit(0);
    }
}