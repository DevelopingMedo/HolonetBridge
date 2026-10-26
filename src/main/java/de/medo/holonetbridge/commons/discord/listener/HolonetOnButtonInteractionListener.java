package de.medo.holonetbridge.commons.discord.listener;

import de.medo.holonetbridge.commons.discord.button.HolonetAddWatcherButton;
import de.medo.holonetbridge.commons.discord.button.HolonetBugReportModalButton;
import de.medo.holonetbridge.commons.discord.button.HolonetFeatureModalButton;
import de.medo.holonetbridge.commons.discord.button.HolonetOpenSupportButton;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

/**
 * JDA {@link ListenerAdapter} for button interactions in the ticket flow. It dispatches each supported custom
 * id to the dedicated button handler in {@code discord.button}; unknown ids are ignored.
 */
@Component
public class HolonetOnButtonInteractionListener extends ListenerAdapter {

    private final HolonetOpenSupportButton openSupportButton;
    private final HolonetBugReportModalButton bugReportButton;
    private final HolonetFeatureModalButton featureButton;
    private final HolonetAddWatcherButton addWatcherButton;

    public HolonetOnButtonInteractionListener(
            HolonetOpenSupportButton openSupportButton,
            HolonetBugReportModalButton bugReportButton,
            HolonetFeatureModalButton featureButton,
            HolonetAddWatcherButton addWatcherButton) {
        this.openSupportButton = openSupportButton;
        this.bugReportButton = bugReportButton;
        this.featureButton = featureButton;
        this.addWatcherButton = addWatcherButton;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        switch (event.getComponentId()) {
            case HolonetOpenSupportButton.BUTTON_ID -> openSupportButton.handle(event);
            case HolonetBugReportModalButton.BUTTON_ID -> bugReportButton.handle(event);
            case HolonetFeatureModalButton.BUTTON_ID -> featureButton.handle(event);
            case HolonetAddWatcherButton.BUTTON_ID -> addWatcherButton.handle(event);
            default -> {
                // Not one of ours; ignore.
            }
        }
    }
}
