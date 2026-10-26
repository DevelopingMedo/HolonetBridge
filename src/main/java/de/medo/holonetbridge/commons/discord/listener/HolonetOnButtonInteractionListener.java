package de.medo.holonetbridge.commons.discord.listener;

import de.medo.holonetbridge.commons.discord.button.HolonetAddWatcherButton;
import de.medo.holonetbridge.commons.discord.button.HolonetBugReportModalButton;
import de.medo.holonetbridge.commons.discord.button.HolonetFeatureModalButton;
import de.medo.holonetbridge.commons.discord.button.HolonetOpenSupportButton;
import de.medo.holonetbridge.commons.discord.button.HolonetSetupRetryButton;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

/**
 * JDA {@link ListenerAdapter} for button interactions in the ticket and {@code /setup} flows. It dispatches
 * each supported custom id to the dedicated button handler in {@code discord.button}; unknown ids are
 * ignored.
 */
@Component
public class HolonetOnButtonInteractionListener extends ListenerAdapter {

    private final HolonetOpenSupportButton openSupportButton;
    private final HolonetBugReportModalButton bugReportButton;
    private final HolonetFeatureModalButton featureButton;
    private final HolonetAddWatcherButton addWatcherButton;
    private final HolonetSetupRetryButton setupRetryButton;

    public HolonetOnButtonInteractionListener(
            HolonetOpenSupportButton openSupportButton,
            HolonetBugReportModalButton bugReportButton,
            HolonetFeatureModalButton featureButton,
            HolonetAddWatcherButton addWatcherButton,
            HolonetSetupRetryButton setupRetryButton) {
        this.openSupportButton = openSupportButton;
        this.bugReportButton = bugReportButton;
        this.featureButton = featureButton;
        this.addWatcherButton = addWatcherButton;
        this.setupRetryButton = setupRetryButton;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        switch (event.getComponentId()) {
            case HolonetOpenSupportButton.BUTTON_ID -> openSupportButton.handle(event);
            case HolonetBugReportModalButton.BUTTON_ID -> bugReportButton.handle(event);
            case HolonetFeatureModalButton.BUTTON_ID -> featureButton.handle(event);
            case HolonetAddWatcherButton.BUTTON_ID -> addWatcherButton.handle(event);
            case HolonetSetupRetryButton.BUTTON_ID -> setupRetryButton.handle(event);
            default -> {
                // Not one of ours; ignore.
            }
        }
    }
}
