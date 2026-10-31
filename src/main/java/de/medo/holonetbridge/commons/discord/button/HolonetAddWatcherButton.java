package de.medo.holonetbridge.commons.discord.button;

import de.holonetbridge.commons.message.SupportMessageCodes;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.springframework.context.MessageSource;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Component;

import java.util.Locale;

import static org.apache.commons.lang3.function.Consumers.nop;

@Component
@Slf4j
public class HolonetAddWatcherButton {

    public static final String BUTTON_ID = "ticket:watcher:add";
    public static final String SELECT_ID = "ticket:watcher:select";

    private final MessageSourceAccessor messageSource;

    public HolonetAddWatcherButton(MessageSource messageSource) {
        this.messageSource = new MessageSourceAccessor(messageSource);
    }

    public void handle(ButtonInteractionEvent event) {
        Locale userLocale = event.getUserLocale().toLocale();
        EntitySelectMenu menu = EntitySelectMenu.create(SELECT_ID, EntitySelectMenu.SelectTarget.USER)
                                                .setRequiredRange(1, 1)
                                                .setPlaceholder(messageSource.getMessage(
                                                        SupportMessageCodes.WATCHER_ADD_PLACEHOLDER, userLocale))
                                                .build();
        event.reply(messageSource.getMessage(SupportMessageCodes.WATCHER_ADD_PROMPT, userLocale))
             .addComponents(ActionRow.of(menu))
             .setEphemeral(true)
             .queue(nop(), failure -> log.error("Watcher selection reply failed", failure));
    }
}
