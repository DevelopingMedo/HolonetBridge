package de.medo.holonetbridge.commons.discord.listener;

import de.medo.holonetbridge.commons.discord.modal.HolonetModalFactory;
import de.medo.holonetbridge.commons.discord.modal.interaction.HolonetBugReportSubmitModalAction;
import de.medo.holonetbridge.commons.discord.modal.interaction.HolonetFeatureSubmittedModalAction;
import de.medo.holonetbridge.commons.discord.modal.interaction.HolonetSetupConnectionSubmitModalAction;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

/**
 * JDA {@link ListenerAdapter} for modal submissions in the ticket and {@code /setup} flows. It dispatches
 * each supported modal id to the dedicated submit action in {@code discord.modal}; unknown ids are ignored.
 */
@Component
public class HolonetOnModalInteractionListener extends ListenerAdapter {

    private final HolonetFeatureSubmittedModalAction featureSubmitted;
    private final HolonetBugReportSubmitModalAction bugReportSubmit;
    private final HolonetSetupConnectionSubmitModalAction setupConnectionSubmit;

    public HolonetOnModalInteractionListener(
            HolonetFeatureSubmittedModalAction featureSubmitted,
            HolonetBugReportSubmitModalAction bugReportSubmit,
            HolonetSetupConnectionSubmitModalAction setupConnectionSubmit) {
        this.featureSubmitted = featureSubmitted;
        this.bugReportSubmit = bugReportSubmit;
        this.setupConnectionSubmit = setupConnectionSubmit;
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        switch (event.getModalId()) {
            case HolonetModalFactory.MODAL_ID_FEATURE_REPORT -> featureSubmitted.handle(event);
            case HolonetModalFactory.MODAL_ID_BUG_REPORT -> bugReportSubmit.handle(event);
            case HolonetModalFactory.MODAL_ID_SETUP_HOLONET -> setupConnectionSubmit.handle(event);
            default -> {
                // Not one of ours; ignore.
            }
        }
    }
}
