using Discord.WebSocket;

namespace ZanikNet.Utils;

public static class Vars
{
    private const string ENV_VAR_TOKEN = "ZANIK_API_TOKEN";
    private const string ENV_VAR_CLAIM_CHANNEL = "ZANIK_CLAIM_CHANNEL";
    private const string ENV_VAR_RESTART_FILE = "ZANIK_RESTART_FILE";
    
    public static string ApiToken => Environment.GetEnvironmentVariable(ENV_VAR_TOKEN) ?? string.Empty;
    public static ulong ClaimChannel => ulong.Parse(Environment.GetEnvironmentVariable(ENV_VAR_CLAIM_CHANNEL) ?? "0");
    public static string RestartFilePath = Environment.GetEnvironmentVariable(ENV_VAR_RESTART_FILE) ?? string.Empty;

    public static void Validate()
    {
        if (ApiToken == string.Empty)
            throw new Exception($"Discord API token not set.{Environment.NewLine}Please set environment variable {ENV_VAR_TOKEN}.");
        if (ClaimChannel == 0)
            throw new Exception($"Channel ID to send credit claim requests to not set.{Environment.NewLine}Please set  environment variable {ENV_VAR_CLAIM_CHANNEL}.");
    }
}