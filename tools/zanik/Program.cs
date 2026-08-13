using System.Reflection;
using Discord;
using Discord.Interactions;
using Discord.WebSocket;
using ZanikNet.Modules;
using ZanikNet.Utils;

namespace ZanikNet
{
    internal class Program
    {
        private static InteractionService _interactionService;
        
        public static async Task Main()
        {
            Vars.Validate();
            
            Globals.Client = new DiscordSocketClient();
            Globals.Client.Log += Logger.Log;

            await Globals.Client.LoginAsync(TokenType.Bot, Vars.ApiToken);
            await Globals.Client.StartAsync();

            _interactionService = new InteractionService(Globals.Client.Rest);
            await _interactionService.AddModulesAsync(Assembly.GetEntryAssembly(), null);

            Globals.Client.InteractionCreated += async (x) =>
            {
                var ctx = new SocketInteractionContext(Globals.Client, x);
                await _interactionService.ExecuteCommandAsync(ctx, null);
            };

            Globals.Client.ButtonExecuted += async (x) =>
            {
                var ctx = new SocketInteractionContext<SocketMessageComponent>(Globals.Client, x);
                await _interactionService.ExecuteCommandAsync(ctx, null);
            };

            Globals.Client.ModalSubmitted += async (x) =>
            {
                var ctx = new SocketInteractionContext<SocketModal>(Globals.Client, x);
                await _interactionService.ExecuteCommandAsync(ctx, null);
            };

            Globals.Client.Ready += async () =>
            {
                await _interactionService.RegisterCommandsGloballyAsync();
            };
            
            await Task.Delay(-1);
        }
    }
}