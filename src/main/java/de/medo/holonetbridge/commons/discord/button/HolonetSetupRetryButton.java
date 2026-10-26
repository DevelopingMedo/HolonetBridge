package de.medo.holonetbridge.commons.discord.button;

import de.medo.holonetbridge.commons.discord.modal.HolonetProviderConnectionModal;
import de.medo.holonetbridge.commons.discord.setup.HolonetSetupDraft;
import de.medo.holonetbridge.commons.discord.setup.HolonetSetupDraftStore;
import de.medo.holonetbridge.commons.provider.HolonetProviderConnectionService;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.springframework.stereotype.Component;

/**
 * Handles the "Erneut versuchen" button attached to a failed {@code /setup} connection validation (custom id
 * {@value #BUTTON_ID}): reopens the Gitea connection modal prefilled with the last-submitted non-secret
 * fields, since Discord does not allow a modal to be opened directly in response to the earlier modal
 * submission that failed.
 */
@Component
public class HolonetSetupRetryButton {

    /** Custom id of the "Erneut versuchen" button. */
    public static final String BUTTON_ID = "setup:retry";

    private final HolonetSetupDraftStore draftStore;
    private final HolonetProviderConnectionService providerConnectionService;

    public HolonetSetupRetryButton(
            HolonetSetupDraftStore draftStore, HolonetProviderConnectionService providerConnectionService) {
        this.draftStore = draftStore;
        this.providerConnectionService = providerConnectionService;
    }

    public void handle(ButtonInteractionEvent event) {
        long userId = event.getUser().getIdLong();
        draftStore.get(userId).ifPresentOrElse(
                draft -> event.replyModal(HolonetProviderConnectionModal.build(tokenRequired(draft), draft)).queue(),
                () -> event.reply("Die Sitzung ist abgelaufen, bitte führe /setup erneut aus.")
                        .setEphemeral(true)
                        .queue());
    }

    private boolean tokenRequired(HolonetSetupDraft draft) {
        return providerConnectionService.activeConnection(draft.guildId()).isEmpty();
    }
}
