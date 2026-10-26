package de.medo.holonetbridge.commons.discord.listener;

import de.medo.holonetbridge.commons.discord.button.HolonetAddWatcherButton;
import de.medo.holonetbridge.commons.discord.select.HolonetGrantWatcherAction;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

/**
 * JDA {@link ListenerAdapter} for entity-select interactions in the ticket flow. It forwards the watcher selection
 * (opened by {@link HolonetAddWatcherButton}) to {@link HolonetGrantWatcherAction}; other selects are ignored.
 */
@Component
public class HolonetOnEntitySelectInteractionListener extends ListenerAdapter {

    private final HolonetGrantWatcherAction grantWatcher;

    public HolonetOnEntitySelectInteractionListener(HolonetGrantWatcherAction grantWatcher) {
        this.grantWatcher = grantWatcher;
    }

    @Override
    public void onEntitySelectInteraction(EntitySelectInteractionEvent event) {
        if (HolonetAddWatcherButton.SELECT_ID.equals(event.getComponentId())) {
            grantWatcher.handle(event);
        }
    }
}
