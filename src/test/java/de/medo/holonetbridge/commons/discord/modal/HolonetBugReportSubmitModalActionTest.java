package de.medo.holonetbridge.commons.discord.modal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.List;

import de.medo.holonetbridge.commons.discord.i18n.I18nProperties;
import de.medo.holonetbridge.commons.discord.i18n.MessageSourceConfiguration;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicket;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicketKind;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

class HolonetBugReportSubmitModalActionTest {

    @ParameterizedTest
    @CsvSource({
            "en-US, What did you do before the error?, Every time",
            "de, Was hast du vor dem Fehler gemacht?, Jedes Mal",
            "hr, Što si napravio prije greške?, Svaki put",
            "ru, Chto ty delal pered oshibkoy?, Kazhdyy raz"
    })
    void submitsTranslatedDescriptionAndPreservesUserInput(String language, String heading, String recurrence) {
        ModalInteractionEvent event = mock(ModalInteractionEvent.class, RETURNS_DEEP_STUBS);
        when(event.getUser().getIdLong()).thenReturn(42L);
        when(event.getGuildLocale()).thenReturn(DiscordLocale.from(language));
        when(event.getValue("title").getAsString()).thenReturn("Original title");
        when(event.getValue("steps").getAsString()).thenReturn("  **Original**\nsteps  ");
        when(event.getValue("expected").getAsString()).thenReturn("expected");
        when(event.getValue("actual").getAsString()).thenReturn("actual");
        when(event.getValue("recurrence").getAsStringList()).thenReturn(List.of("always"));
        HolonetTicketSubmitter submitter = mock(HolonetTicketSubmitter.class);
        var messages = new MessageSourceConfiguration().messageSource(new I18nProperties(null));

        new HolonetBugReportSubmitModalAction(submitter, messages).handle(event);

        var ticket = ArgumentCaptor.forClass(HolonetTicket.class);
        verify(submitter).submit(eq(event), eq(42L), eq(HolonetTicketKind.BUG), ticket.capture());
        assertThat(ticket.getValue().getTitle()).isEqualTo("Original title");
        assertThat(ticket.getValue().getDescription())
                .startsWith("## " + heading + "\n\n  **Original**\nsteps  ")
                .contains("\n\nexpected\n\n", "\n\nactual\n\n")
                .endsWith(recurrence);
    }
}
