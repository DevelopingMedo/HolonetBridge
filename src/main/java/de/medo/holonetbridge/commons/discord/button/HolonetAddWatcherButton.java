package de.medo.holonetbridge.commons.discord.button;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.springframework.stereotype.Component;

/**
 * Handles the "Beobachter hinzufügen" button (custom id {@code ticket:watcher:add}) by opening an ephemeral
 * user selector (custom id {@code ticket:watcher:select}) from which the requester picks one person to grant
 * channel access to.
 */
@Component
public class HolonetAddWatcherButton {

    /** Custom id of the "Beobachter hinzufügen" button. */
    public static final String BUTTON_ID = "ticket:watcher:add";

    /** Custom id of the user selector menu this button opens. */
    public static final String SELECT_ID = "ticket:watcher:select";

    public void handle(ButtonInteractionEvent event) {
        EntitySelectMenu menu = EntitySelectMenu.create(SELECT_ID, EntitySelectMenu.SelectTarget.USER)
                .setRequiredRange(1, 1)
                .setPlaceholder("Person auswählen")
                .build();
        event.reply("Wähle eine Person aus, die den Kanal sehen und kommentieren darf.")
                .addComponents(ActionRow.of(menu))
                .setEphemeral(true)
                .queue();
    }
}
