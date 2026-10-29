package de.medo.holonetbridge.commons.discord.modal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Locale;

import de.medo.holonetbridge.commons.discord.i18n.I18nProperties;
import de.medo.holonetbridge.commons.discord.i18n.MessageSourceConfiguration;
import de.medo.holonetbridge.commons.discord.modal.interaction.HolonetBugReportSubmitModalAction;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicket;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicketKind;
import de.medo.holonetbridge.commons.domain.HolonetReportFormData;
import de.medo.holonetbridge.commons.service.HolonetBotInteractionSessionService;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

class HolonetBugReportSubmitModalActionTest {

    @Test
    void savesReceivedFieldsBeforeFormattingOrLocalizationCanFail() {
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
        when(dataService.getOrCreate(anyLong(), anyLong(), anyLong(), any(), eq(HolonetTicketKind.BUG)))
                .thenReturn(data);
        var messages = mock(org.springframework.context.MessageSource.class);
        when(messages.getMessage("bug.recurrence-always", null, Locale.US))
                .thenThrow(new IllegalStateException("Formatting unavailable"));
        var submitter = mock(HolonetTicketSubmitter.class);

        assertThatThrownBy(() -> new HolonetBugReportSubmitModalAction(submitter, messages, dataService).handle(event))
                .isInstanceOf(IllegalStateException.class);

        verify(dataService).save(data);
        assertThat(data.getTitle()).isEqualTo("Original title");
        assertThat(data.getSteps()).isEqualTo("Original steps");
        verifyNoInteractions(submitter);
    }

    @ParameterizedTest
    @CsvSource({
            "en-US, What did you do before the error?, Every time, always",
            "de, Was hast du vor dem Fehler gemacht?, Jedes Mal, always",
            "hr, Što si napravio prije greške?, Svaki put, always",
            "ru, Chto ty delal pered oshibkoy?, Kazhdyy raz, always",
            "de, Was hast du vor dem Fehler gemacht?, Jedes Mal, Jedes Mal"
    })
    void submitsTranslatedDescriptionAndPreservesUserInput(String language, String heading, String recurrence, String submittedRecurrence) {
        ModalInteractionEvent event = mock(ModalInteractionEvent.class, RETURNS_DEEP_STUBS);
        when(event.getUser().getIdLong()).thenReturn(42L);
        when(event.getGuildLocale()).thenReturn(DiscordLocale.from(language));
        when(event.getUserLocale()).thenReturn(DiscordLocale.from(language));
        when(event.getValue("title").getAsString()).thenReturn("Original title");
        when(event.getValue("steps").getAsString()).thenReturn("  **Original**\nsteps  ");
        when(event.getValue("expected").getAsString()).thenReturn("expected");
        when(event.getValue("actual").getAsString()).thenReturn("actual");
        when(event.getValue("recurrence").getAsStringList()).thenReturn(List.of(submittedRecurrence));
        HolonetTicketSubmitter submitter = mock(HolonetTicketSubmitter.class);
        var messages = new MessageSourceConfiguration().messageSource(new I18nProperties(null));

        var formDataService = mock(HolonetBotInteractionSessionService.class);
        var data = new HolonetReportFormData();
        when(formDataService.getOrCreate(anyLong(), eq(42L), anyLong(), any(), eq(HolonetTicketKind.BUG)))
                .thenReturn(data);
        new HolonetBugReportSubmitModalAction(submitter, messages, formDataService).handle(event);

        var order = inOrder(formDataService, submitter);
        order.verify(formDataService).save(data);
        order.verify(submitter).submit(eq(event), eq(42L), eq(HolonetTicketKind.BUG), any());
        assertThat(data.getSteps()).isEqualTo("  **Original**\nsteps  ");
        assertThat(data.getTitle()).isEqualTo("Original title");
        assertThat(data.getRecurrence()).isEqualTo("always");

        var ticket = ArgumentCaptor.forClass(HolonetTicket.class);
        verify(submitter).submit(eq(event), eq(42L), eq(HolonetTicketKind.BUG), ticket.capture());
        assertThat(ticket.getValue().getTitle()).isEqualTo("Original title");
        assertThat(ticket.getValue().getDescription())
                .startsWith("## " + heading + "\n\n  **Original**\nsteps  ")
                .contains("\n\nexpected\n\n", "\n\nactual\n\n")
                .endsWith(recurrence);
    }
}
