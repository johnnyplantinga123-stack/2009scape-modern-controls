using Discord;
using Discord.Interactions;
using Discord.WebSocket;
using ZanikNet.Utils;

namespace ZanikNet.Modules;

public class CreditClaimModule : InteractionModuleBase
{
    private const string CreditClaimUsernameInput = "CreditClaim.Input.Username";
    private const string CreditClaimAmountInput = "CreditClaim.Input.Amount";
    private const string CreditClaimReasonInput = "CreditClaim.Input.Reason";
    private const string CreditClaimProofInput = "CreditClaim.Input.Proof";
    private const string CreditClaimMarkDone = "CreditClaim.Response.MarkDone";
    private const string CreditClaimMarkDenied = "CreditClaim.Response.MarkDenied";
    private const string CreditClaimConfirmDone = "CreditClaim.Response.ConfirmDone";
    private const string CreditClaimConfirmDenied = "CreditClaim.Response.ConfirmDenied";
    private const string CreditClaimConfirmComments = "CreditClaim.Response.ConfirmComments";
    private const string CreditClaimModal = "CreditClaim.Modal";

    [SlashCommand("claim", "File a claim for credits")]
    public async Task FileClaim()
    {
        await RespondWithModalAsync<ClaimFormModal>(CreditClaimModal);
    }
    
    private static ComponentBuilderV2 GetAdminEmbed(string username, string amount, string reason, string proof)
    {
        var buttonBuilder = new ButtonBuilder()
            .WithCustomId(CreditClaimMarkDone)
            .WithLabel("Resolve Claim")
            .WithStyle(ButtonStyle.Success);
        
        var denyButtonBuilder = new ButtonBuilder()
            .WithCustomId(CreditClaimMarkDenied)
            .WithLabel("Deny Claim")
            .WithStyle(ButtonStyle.Danger);

        var container = new ContainerBuilder()
            .WithAccentColor(Color.Blue)
            .WithTextDisplay($"Username: {username}")
            .WithTextDisplay($"Amount: {amount}")
            .WithTextDisplay($"Reason: {reason}")
            .WithTextDisplay("Proof")
            .WithTextDisplay(proof);

        var builder = new ComponentBuilderV2()
            .WithContainer(container)
            .WithActionRow([denyButtonBuilder, buttonBuilder]);
        
        return builder;
    }

    public class ClaimConfirmationModule : InteractionModuleBase<SocketInteractionContext<SocketModal>>
    {
        [ModalInteraction(CreditClaimModal)]
        public async Task ModalResponse(ClaimFormModal modal)
        {
            var channel = await Globals.Client.GetChannelAsync(Vars.ClaimChannel);
            var embed = GetAdminEmbed(modal.Username, modal.Amount, modal.Reason, modal.Proof);
            await ((IMessageChannel)channel).SendMessageAsync(components: embed.Build());
            await RespondAsync("✅ Credit Claim Submitted");
        }

        [ModalInteraction(CreditClaimConfirmDone)]
        public async Task ModalConfirmDoneResponse(ClaimResponseConfirmModal confirm)
        {
            await Context.Interaction.UpdateAsync(m => m.Components = ClaimGrantedEmbed(confirm.Username, confirm.Amount, confirm.Reason, confirm.Comments).Build());
        }

        [ModalInteraction(CreditClaimConfirmDenied)]
        public async Task ModalConfirmDeniedResponse(ClaimResponseConfirmModal confirm)
        {
            await Context.Interaction.UpdateAsync(m => m.Components = ClaimDeniedEmbed(confirm.Username, confirm.Amount, confirm.Reason, confirm.Comments).Build());
        }
        
        private static ComponentBuilderV2 ClaimGrantedEmbed(string username, string amount, string reason, string comments)
        {
            var container = new ContainerBuilder()
                .WithAccentColor(Color.Green)
                .WithTextDisplay("# Granted Claim")
                .WithTextDisplay($"Username: {username}")
                .WithTextDisplay($"Amount: {amount}")
                .WithTextDisplay($"Reason: {reason}");
                
            if (!string.IsNullOrEmpty(comments))
                container = container.WithTextDisplay($"Comments: {comments}");
            

            var builder = new ComponentBuilderV2()
                .WithContainer(container);

            return builder;
        }

        private static ComponentBuilderV2 ClaimDeniedEmbed(string username, string amount, string reason, string comments)
        {
            var container = new ContainerBuilder()
                .WithAccentColor(Color.Red)
                .WithTextDisplay("# Denied Claim")
                .WithTextDisplay($"Username: {username}")
                .WithTextDisplay($"Amount: {amount}")
                .WithTextDisplay($"Reason: {reason}");
                
            if (!string.IsNullOrEmpty(comments))
                container = container.WithTextDisplay($"Comments: {comments}");
            
            var builder = new ComponentBuilderV2()
                .WithContainer(container);
            
            return builder;
        }
    }
    
    public class ClaimResponseModule : InteractionModuleBase<SocketInteractionContext<SocketMessageComponent>>
    {
        [ComponentInteraction(CreditClaimMarkDone)]
        public async Task MarkDone()
        {
            var msgUsername = GetTextInComponentContaining("Username", Context.Interaction.Message).Replace("Username: ", "");
            var msgAmount = GetTextInComponentContaining("Amount", Context.Interaction.Message).Replace("Amount: ", "");
            var msgReason = GetTextInComponentContaining("Reason", Context.Interaction.Message).Replace("Reason: ", "");
            await Context.Interaction.RespondWithModalAsync<ClaimResponseConfirmModal>(CreditClaimConfirmDone,
                modifyModal: (modal) =>
                {
                    modal.Title = "✅Approval Confirmation✅";
                    modal.UpdateTextInput(CreditClaimUsernameInput, x => x.Value = msgUsername);
                    modal.UpdateTextInput(CreditClaimAmountInput, x => x.Value = msgAmount);
                    modal.UpdateTextInput(CreditClaimReasonInput, x => x.Value = msgReason);
                });
        }

        [ComponentInteraction(CreditClaimMarkDenied)]
        public async Task MarkDenied()
        {
            var msgUsername = GetTextInComponentContaining("Username", Context.Interaction.Message).Replace("Username: ", "");
            var msgAmount = GetTextInComponentContaining("Amount", Context.Interaction.Message).Replace("Amount: ", "");
            var msgReason = GetTextInComponentContaining("Reason", Context.Interaction.Message).Replace("Reason: ", "");
            await Context.Interaction.RespondWithModalAsync<ClaimResponseConfirmModal>(CreditClaimConfirmDenied,
                modifyModal: (modal) =>
                {
                    modal.Title = "❌Denial Confirmation❌";
                    modal.UpdateTextInput(CreditClaimUsernameInput, x => x.Value = msgUsername);
                    modal.UpdateTextInput(CreditClaimAmountInput, x => x.Value = msgAmount);
                    modal.UpdateTextInput(CreditClaimReasonInput, x => x.Value = msgReason);
                });
        }
        
        private static string GetTextInComponentContaining(string value, SocketUserMessage message)
        {
            var container = (ContainerComponent)message.Components.First(c => c.Type == ComponentType.Container);
            var component = container.Components.First(c => c.Type == ComponentType.TextDisplay && ((TextDisplayComponent)c).Content.Contains(value));
            return ((TextDisplayComponent)component).Content;
        }
    }

    public class ClaimFormModal : IModal
    {
        public string Title => "Credit Claim Form";
        
        [InputLabel("Username")]
        [ModalTextInput(CreditClaimUsernameInput, minLength: 1, style: TextInputStyle.Short)]
        public string Username { get; set; }
        
        [InputLabel("Amount")]
        [ModalTextInput(CreditClaimAmountInput, minLength: 1, maxLength: 3, style: TextInputStyle.Short, initValue: "0")]
        public string Amount { get; set; }
        
        [InputLabel("Reason (Testing, Record, MR, etc)")]
        [ModalTextInput(CreditClaimReasonInput, minLength: 1, style: TextInputStyle.Short)]
        public string Reason { get; set; }
        
        [InputLabel("Proof - Links to QA posts, screenshots, etc")]
        [ModalTextInput(CreditClaimProofInput, minLength: 1, style: TextInputStyle.Paragraph)]
        public string Proof { get; set; }
    }

    public class ClaimResponseConfirmModal : IModal
    {
        public string Title => "Confirmation";
        
        [InputLabel("Username")]
        [ModalTextInput(CreditClaimUsernameInput, minLength: 1, style: TextInputStyle.Short)]
        public string Username { get; set; }
        
        [InputLabel("Amount")]
        [ModalTextInput(CreditClaimAmountInput, minLength: 1, maxLength: 3, style: TextInputStyle.Short, initValue: "0")]
        public string Amount { get; set; }
        
        [InputLabel("Reason")]
        [ModalTextInput(CreditClaimReasonInput, minLength: 1, style: TextInputStyle.Short)]
        public string Reason { get; set; }
        
        [RequiredInput(false)]
        [InputLabel("Comments")]
        [ModalTextInput(CreditClaimConfirmComments, style: TextInputStyle.Short)]
        public string Comments { get; set; }
    }
}