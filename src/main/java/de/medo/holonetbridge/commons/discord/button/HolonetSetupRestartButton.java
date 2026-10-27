package de.medo.holonetbridge.commons.discord.button;

import de.medo.holonetbridge.commons.discord.modal.HolonetProviderConnectionModal;
import de.medo.holonetbridge.commons.discord.setup.HolonetSetupSessionStore;
import de.medo.holonetbridge.commons.provider.HolonetProviderConnectionService;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.springframework.stereotype.Component;

/**
 * Handles the "Neu starten" button (custom id {@value #BUTTON_ID}) on the resume-or-restart prompt {@code
 * /setup} shows when an unfinished attempt for this guild/user already exists (see {@link
 * de.medo.holonetbridge.commons.discord.command.HolonetSetupCommandAction}): discards that attempt and
 * opens a fresh, empty Gitea connection modal, discarding whatever was entered before.
 */
@Component
public class HolonetSetupRestartButton {

    /** Custom id of the "Neu starten" button. */
    public static final String BUTTON_ID = "setup:restart";

    private final HolonetSetupSessionStore sessionStore;
    private final HolonetProviderConnectionService providerConnectionService;

    public HolonetSetupRestartButton(
            HolonetSetupSessionStore sessionStore, HolonetProviderConnectionService providerConnectionService) {
        this.sessionStore = sessionStore;
        this.providerConnectionService = providerConnectionService;
    }

    public void handle(ButtonInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("/setup kann nur innerhalb eines Servers ausgeführt werden.")
                    .setEphemeral(true)
                    .queue();
            return;
        }
        long guildId = guild.getIdLong();
        long userId = event.getUser().getIdLong();
        sessionStore.discard(guildId, userId);

        boolean tokenRequired = providerConnectionService.activeConnection(guildId).isEmpty();
        event.replyModal(HolonetProviderConnectionModal.build(tokenRequired, null)).queue();
    }
}
