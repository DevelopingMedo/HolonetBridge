package de.medo.holonetbridge.commons.discord.modal.interaction;

import de.medo.holonetbridge.commons.discord.modal.HolonetTicketReportModalFactory;
import de.medo.holonetbridge.commons.discord.modal.HolonetTicketSubmitter;
import de.medo.holonetbridge.commons.domain.HolonetReportFormData;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicketKind;
import de.medo.holonetbridge.commons.service.HolonetBotInteractionSessionService;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import org.springframework.stereotype.Component;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataAccessException;

@Component
public class HolonetFeatureSubmittedModalAction {

    private final HolonetTicketSubmitter ticketSubmitter;
    private final HolonetBotInteractionSessionService formDataService;
    private final MessageSource messageSource;

    public HolonetFeatureSubmittedModalAction(HolonetTicketSubmitter ticketSubmitter,
            HolonetBotInteractionSessionService formDataService, MessageSource messageSource) {
        this.ticketSubmitter = ticketSubmitter;
        this.formDataService = formDataService;
        this.messageSource = messageSource;
    }

    public void handle(ModalInteractionEvent event) {
        if (event.getGuild() == null) {
            event.reply(messageSource.getMessage("support.guild-only", null, event.getUserLocale().toLocale()))
                    .setEphemeral(true).queue();
            return;
        }
        long userId = event.getUser().getIdLong();
        HolonetReportFormData data;
        try {
            data = formDataService.getOrCreate(event.getGuild().getIdLong(), userId,
                    event.getUserLocale().toLocale(), HolonetTicketKind.FEATURE);
            data.setTitle(event.getValue(HolonetTicketReportModalFactory.INPUT_TITLE).getAsString());
            data.setDescription(event.getValue(HolonetTicketReportModalFactory.INPUT_DESCRIPTION).getAsString());
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
