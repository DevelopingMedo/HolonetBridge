package de.medo.holonetbridge.commons.discord.modal.interaction;

import de.medo.holonetbridge.commons.discord.modal.HolonetTicketReportModalFactory;
import de.medo.holonetbridge.commons.discord.modal.HolonetTicketSubmitter;
import de.medo.holonetbridge.commons.domain.HolonetReportFormData;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicketKind;
import de.medo.holonetbridge.commons.service.HolonetBotInteractionSessionService;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Component
public class HolonetBugReportSubmitModalAction {

    private final HolonetTicketSubmitter ticketSubmitter;
    private final MessageSource messageSource;
    private final HolonetBotInteractionSessionService formDataService;

    public HolonetBugReportSubmitModalAction(HolonetTicketSubmitter ticketSubmitter, MessageSource messageSource,
            HolonetBotInteractionSessionService formDataService) {
        this.ticketSubmitter = ticketSubmitter;
        this.messageSource = messageSource;
        this.formDataService = formDataService;
    }

    public void handle(ModalInteractionEvent event) {
        if (event.getGuild() == null) {
            event.reply(messageSource.getMessage("support.guild-only", null, event.getUserLocale().toLocale()))
                    .setEphemeral(true).queue();
            return;
        }
        long userId = event.getUser().getIdLong();
        String steps = event.getValue(HolonetTicketReportModalFactory.INPUT_STEPS).getAsString();
        String expected = event.getValue(HolonetTicketReportModalFactory.INPUT_EXPECTED).getAsString();
        String actual = event.getValue(HolonetTicketReportModalFactory.INPUT_ACTUAL).getAsString();
        String recurrence = HolonetTicketReportModalFactory.normalizeRecurrence(
                event.getValue(HolonetTicketReportModalFactory.INPUT_RECURRENCE).getAsStringList().getFirst());
        String title = event.getValue(HolonetTicketReportModalFactory.INPUT_TITLE).getAsString();
        HolonetReportFormData data;
        try {
            data = formDataService.getOrCreate(event.getGuild().getIdLong(), userId,
                    event.getUserLocale().toLocale(), HolonetTicketKind.BUG);
            data.setTitle(title);
            data.setSteps(steps);
            data.setExpected(expected);
            data.setActual(actual);
            data.setRecurrence(recurrence);
            data.setGuildName(event.getGuild().getName());
            data.setUsername(event.getUser().getName());
            formDataService.save(data);
        } catch (DataAccessException failure) {
            event.reply(messageSource.getMessage("ticket.draft-save-failed", null, event.getUserLocale().toLocale()))
                    .setEphemeral(true).queue();
            return;
        }
        ticketSubmitter.handle(event, data);
    }
}
