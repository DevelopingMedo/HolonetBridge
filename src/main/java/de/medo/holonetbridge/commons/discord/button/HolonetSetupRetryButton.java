package de.medo.holonetbridge.commons.discord.button;

import java.util.Locale;

import de.medo.holonetbridge.commons.discord.modal.HolonetProviderConnectionModal;
import de.medo.holonetbridge.commons.discord.setup.HolonetSetupDraft;
import de.medo.holonetbridge.commons.discord.setup.HolonetSetupDraftStore;
import de.medo.holonetbridge.commons.provider.HolonetProviderConnectionService;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/**
 * Handles the "Erneut versuchen" button attached to a failed {@code /setup} connection validation (custom id
 * {@value #BUTTON_ID}): reopens the Gitea connection modal prefilled with the last-submitted non-secret
 * fields, since Discord does not allow a modal to be opened directly in response to the earlier modal
 * submission that failed.
 */
@Component
public class HolonetSetupRetryButton {

    private final MessageSource messageSource;

    /** Custom id of the "Erneut versuchen" button. */
    public static final String BUTTON_ID = "setup:retry";

    private final HolonetSetupDraftStore draftStore;
    private final HolonetProviderConnectionService providerConnectionService;

    public HolonetSetupRetryButton(
            MessageSource messageSource,
            HolonetSetupDraftStore draftStore, HolonetProviderConnectionService providerConnectionService) {
        this.messageSource = messageSource;
        this.draftStore = draftStore;
        this.providerConnectionService = providerConnectionService;
    }

    public void handle(ButtonInteractionEvent event) {
        Locale userLocale = event.getUserLocale().toLocale();
        long userId = event.getUser().getIdLong();
        draftStore.get(userId).ifPresentOrElse(
                draft -> event.replyModal(HolonetProviderConnectionModal.build(tokenRequired(draft), draft, messageSource, userLocale)).queue(),
                () -> event.reply(messageSource.getMessage("setup.expired", null, userLocale))
                        .setEphemeral(true)
                        .queue());
    }

    private boolean tokenRequired(HolonetSetupDraft draft) {
        return providerConnectionService.activeConnection(draft.guildId()).isEmpty();
    }
}
