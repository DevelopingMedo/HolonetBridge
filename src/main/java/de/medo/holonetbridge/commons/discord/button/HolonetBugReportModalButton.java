package de.medo.holonetbridge.commons.discord.button;

import de.medo.holonetbridge.commons.discord.modal.HolonetTicketReportModalFactory;
import de.medo.holonetbridge.commons.discord.ticketing.HolonetTicketCreationSessionStore;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicketKind;
import de.medo.holonetbridge.commons.service.HolonetBotInteractionSessionService;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Component
public class HolonetBugReportModalButton {
    public static final String BUTTON_ID = "ticket:kind:bug";

    private final MessageSource messageSource;
    private final HolonetTicketCreationSessionStore sessionStore;
    private final HolonetTicketReportModalFactory modalFactory;
    private final HolonetBotInteractionSessionService formDataService;

    public HolonetBugReportModalButton(MessageSource messageSource, HolonetTicketCreationSessionStore sessionStore,
                                       HolonetTicketReportModalFactory modalFactory, HolonetBotInteractionSessionService formDataService) {
        this.messageSource = messageSource;
        this.sessionStore = sessionStore;
        this.modalFactory = modalFactory;
        this.formDataService = formDataService;
    }

    public void handle(ButtonInteractionEvent event) {
        var locale = event.getUserLocale().toLocale();
        if (event.getGuild() == null) {
            event.reply(messageSource.getMessage("support.guild-only", null, locale)).setEphemeral(true).queue();
            return;
        }
        try {
            var data = formDataService.getOrCreate(event.getGuild().getIdLong(), event.getUser().getIdLong(),
                    event.getChannel().getIdLong(), locale, HolonetTicketKind.BUG);
            sessionStore.get(event.getGuild().getIdLong(), event.getChannel().getIdLong(), event.getUser().getIdLong())
                    .ifPresent(session -> session.kind(HolonetTicketKind.BUG));
            event.replyModal(modalFactory.createBugReportModal(data, locale)).queue();
        } catch (DataAccessException failure) {
            event.reply(messageSource.getMessage("ticket.draft-load-failed", null, locale)).setEphemeral(true).queue();
        }
    }
}
