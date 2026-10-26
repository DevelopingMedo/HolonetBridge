package de.medo.holonetbridge.commons.discord.listener;

import de.medo.holonetbridge.commons.discord.button.HolonetBugReportModalButton;
import de.medo.holonetbridge.commons.discord.button.HolonetFeatureModalButton;
import de.medo.holonetbridge.commons.discord.modal.HolonetBugReportSubmitModalAction;
import de.medo.holonetbridge.commons.discord.modal.HolonetFeatureSubmittedModalAction;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

/**
 * JDA {@link ListenerAdapter} for modal submissions in the ticket flow. It dispatches each supported modal id to
 * the dedicated submit action in {@code discord.modal}; unknown ids are ignored.
 */
@Component
public class HolonetOnModalInteractionListener extends ListenerAdapter {

    private final HolonetFeatureSubmittedModalAction featureSubmitted;
    private final HolonetBugReportSubmitModalAction bugReportSubmit;

    public HolonetOnModalInteractionListener(
            HolonetFeatureSubmittedModalAction featureSubmitted,
            HolonetBugReportSubmitModalAction bugReportSubmit) {
        this.featureSubmitted = featureSubmitted;
        this.bugReportSubmit = bugReportSubmit;
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        switch (event.getModalId()) {
            case HolonetFeatureModalButton.MODAL_ID -> featureSubmitted.handle(event);
            case HolonetBugReportModalButton.MODAL_ID -> bugReportSubmit.handle(event);
            default -> {
                // Not one of ours; ignore.
            }
        }
    }
}
