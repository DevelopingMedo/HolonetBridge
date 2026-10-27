package de.medo.holonetbridge.commons.discord.button;

import de.medo.holonetbridge.commons.discord.modal.HolonetProviderConnectionModal;
import de.medo.holonetbridge.commons.discord.setup.HolonetSetupSessionStore;
import de.medo.holonetbridge.commons.provider.HolonetProviderConnectionService;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.springframework.stereotype.Component;

/**
 * Handles the "Fortsetzen" button (custom id {@value #BUTTON_ID}): reopens the Gitea connection modal
 * prefilled with the last-submitted non-secret fields, since Discord does not allow a modal to be opened
 * directly in response to the earlier interaction that offered this button.
 *
 * <p>Shown in two places that both boil down to "there is an unfinished {@code /setup} attempt for this
 * guild/user": directly on a failed connection validation, and on the resume-or-restart prompt {@code
 * /setup} shows instead of a fresh modal when such an attempt already exists (see {@link
 * de.medo.holonetbridge.commons.discord.command.HolonetSetupCommandAction}).
 */
@Component
public class HolonetSetupRetryButton {

    /** Custom id of the "Fortsetzen" button. */
    public static final String BUTTON_ID = "setup:retry";

    private final HolonetSetupSessionStore sessionStore;
    private final HolonetProviderConnectionService providerConnectionService;

    public HolonetSetupRetryButton(
            HolonetSetupSessionStore sessionStore, HolonetProviderConnectionService providerConnectionService) {
        this.sessionStore = sessionStore;
        this.providerConnectionService = providerConnectionService;
    }

    public void handle(ButtonInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            replyExpired(event);
            return;
        }
        long guildId = guild.getIdLong();
        long userId = event.getUser().getIdLong();
        sessionStore.findUnfinished(guildId, userId).ifPresentOrElse(
                draft -> event.replyModal(HolonetProviderConnectionModal.build(tokenRequired(guildId), draft)).queue(),
                () -> replyExpired(event));
    }

    private static void replyExpired(ButtonInteractionEvent event) {
        event.reply("Die Sitzung ist abgelaufen, bitte führe /setup erneut aus.")
                .setEphemeral(true)
                .queue();
    }

    private boolean tokenRequired(long guildId) {
        return providerConnectionService.activeConnection(guildId).isEmpty();
    }
}
