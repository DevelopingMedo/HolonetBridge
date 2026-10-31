package de.medo.holonetbridge.commons.discord.button;

import de.holonetbridge.commons.message.SupportMessageCodes;
import de.medo.holonetbridge.commons.discord.modal.HolonetTicketReportModalFactory;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicketKind;
import de.medo.holonetbridge.commons.service.HolonetBotInteractionSessionService;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.springframework.context.MessageSource;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.util.Locale;

import static org.apache.commons.lang3.function.Consumers.nop;

@Component
@Slf4j
public class HolonetReportFeatureButton {

    public static final String BUTTON_ID = "ticket:kind:feature";

    private final MessageSourceAccessor messageSource;
    private final HolonetTicketReportModalFactory modalFactory;
    private final HolonetBotInteractionSessionService formDataService;

    public HolonetReportFeatureButton(MessageSource messageSource,
                                      HolonetTicketReportModalFactory modalFactory,
                                      HolonetBotInteractionSessionService formDataService) {
        this.messageSource = new MessageSourceAccessor(messageSource);
        this.modalFactory = modalFactory;
        this.formDataService = formDataService;
    }

    public void handle(ButtonInteractionEvent event) {
        Locale userLocale = event.getUserLocale().toLocale();
        try {
            var data = formDataService.getOrCreate(event.getGuild().getIdLong(), event.getUser().getIdLong(),
                                                   userLocale, HolonetTicketKind.FEATURE);
            // A modal must be the initial interaction response; replying or deferring first would prevent opening it.
            event.replyModal(modalFactory.createFeatureModal(data, userLocale))
                 .queue(nop(), failure -> log.error("Feature report modal reply failed", failure));
        } catch (DataAccessException failure) {
            log.error("Feature report draft could not be loaded", failure);
            event.reply(messageSource.getMessage(SupportMessageCodes.REPORT_DRAFT_LOAD_FAILURE, userLocale))
                 .setEphemeral(true)
                 .queue();
        }
    }
}
