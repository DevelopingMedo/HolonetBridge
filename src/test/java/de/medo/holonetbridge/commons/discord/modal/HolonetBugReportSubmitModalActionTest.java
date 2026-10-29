package de.medo.holonetbridge.commons.discord.modal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.List;

import de.medo.holonetbridge.commons.discord.modal.interaction.HolonetBugReportSubmitModalAction;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicketKind;
import de.medo.holonetbridge.commons.domain.HolonetReportFormData;
import de.medo.holonetbridge.commons.service.HolonetBotInteractionSessionService;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import org.junit.jupiter.api.Test;

class HolonetBugReportSubmitModalActionTest {

    @Test
    void savesReceivedFieldsBeforeFurtherProcessingCanFail() {
        var event = mock(ModalInteractionEvent.class, RETURNS_DEEP_STUBS);
        when(event.getGuildLocale()).thenReturn(DiscordLocale.ENGLISH_US);
        when(event.getUserLocale()).thenReturn(DiscordLocale.ENGLISH_US);
        when(event.getValue("title").getAsString()).thenReturn("Original title");
        when(event.getValue("steps").getAsString()).thenReturn("Original steps");
        when(event.getValue("expected").getAsString()).thenReturn("Expected");
        when(event.getValue("actual").getAsString()).thenReturn("Actual");
        when(event.getValue("recurrence").getAsStringList()).thenReturn(List.of("always"));
        var dataService = mock(HolonetBotInteractionSessionService.class);
        var data = new HolonetReportFormData();
        when(dataService.getOrCreate(anyLong(), anyLong(), any(), eq(HolonetTicketKind.BUG)))
                .thenReturn(data);
        var messages = mock(org.springframework.context.MessageSource.class);
        var submitter = mock(HolonetTicketSubmitter.class);
        doThrow(new IllegalStateException("Later processing failed")).when(submitter).handle(event, data);

        assertThatThrownBy(() -> new HolonetBugReportSubmitModalAction(submitter, messages, dataService).handle(event))
                .isInstanceOf(IllegalStateException.class);

        var order = inOrder(dataService, submitter);
        order.verify(dataService).save(data);
        order.verify(submitter).handle(event, data);
        assertThat(data.getTitle()).isEqualTo("Original title");
        assertThat(data.getSteps()).isEqualTo("Original steps");
    }

}
