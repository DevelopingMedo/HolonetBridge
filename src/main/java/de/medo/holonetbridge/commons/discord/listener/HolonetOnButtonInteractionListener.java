package de.medo.holonetbridge.commons.discord.listener;

import de.medo.holonetbridge.commons.discord.button.HolonetAddWatcherButton;
import de.medo.holonetbridge.commons.discord.button.HolonetReportBugButton;
import de.medo.holonetbridge.commons.discord.button.HolonetReportFeatureButton;
import de.medo.holonetbridge.commons.discord.button.HolonetOpenSupportButton;
import de.medo.holonetbridge.commons.discord.button.HolonetCloseTicketButton;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

@Component
public class HolonetOnButtonInteractionListener extends ListenerAdapter {

    private final HolonetOpenSupportButton openSupportButton;
    private final HolonetReportBugButton reportBugButton;
    private final HolonetReportFeatureButton reportFeatureButton;
    private final HolonetAddWatcherButton addWatcherButton;
    private final HolonetCloseTicketButton closeTicketButton;

    public HolonetOnButtonInteractionListener(
            HolonetOpenSupportButton openSupportButton,
            HolonetReportBugButton reportBugButton,
            HolonetReportFeatureButton reportFeatureButton,
            HolonetAddWatcherButton addWatcherButton,
            HolonetCloseTicketButton closeTicketButton) {
        this.openSupportButton = openSupportButton;
        this.reportBugButton = reportBugButton;
        this.reportFeatureButton = reportFeatureButton;
        this.addWatcherButton = addWatcherButton;
        this.closeTicketButton = closeTicketButton;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!event.isFromGuild()) return;
        switch (event.getComponentId()) {
            case HolonetOpenSupportButton.BUTTON_ID -> openSupportButton.handle(event);
            case HolonetReportBugButton.BUTTON_ID -> reportBugButton.handle(event);
            case HolonetReportFeatureButton.BUTTON_ID -> reportFeatureButton.handle(event);
            case HolonetAddWatcherButton.BUTTON_ID -> addWatcherButton.handle(event);
            case HolonetCloseTicketButton.BUTTON_ID -> closeTicketButton.handle(event);
            default -> {
            }
        }
    }
}
