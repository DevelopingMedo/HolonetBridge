package de.medo.holonetbridge.commons.discord.button;

import de.medo.holonetbridge.commons.discord.i18n.Texts;
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

    private final Texts texts;

    /** Custom id of the "Beobachter hinzufügen" button. */
    public static final String BUTTON_ID = "ticket:watcher:add";

    /** Custom id of the user selector menu this button opens. */
    public static final String SELECT_ID = "ticket:watcher:select";

    public HolonetAddWatcherButton(Texts texts) {
        this.texts = texts;
    }

    public void handle(ButtonInteractionEvent event) {
        EntitySelectMenu menu = EntitySelectMenu.create(SELECT_ID, EntitySelectMenu.SelectTarget.USER)
                .setRequiredRange(1, 1)
                .setPlaceholder(texts.user(event, "watcher.placeholder"))
                .build();
        event.reply(texts.user(event, "watcher.prompt"))
                .addComponents(ActionRow.of(menu))
                .setEphemeral(true)
                .queue();
    }
}
