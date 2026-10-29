package de.medo.holonetbridge.commons.discord.modal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Locale;

import de.medo.holonetbridge.commons.discord.button.HolonetBugReportModalButton;
import de.medo.holonetbridge.commons.discord.button.HolonetFeatureModalButton;
import de.medo.holonetbridge.commons.discord.i18n.I18nProperties;
import de.medo.holonetbridge.commons.discord.i18n.MessageSourceConfiguration;
import de.medo.holonetbridge.commons.discord.listener.HolonetOnModalInteractionListener;
import de.medo.holonetbridge.commons.discord.modal.interaction.HolonetBugReportSubmitModalAction;
import de.medo.holonetbridge.commons.discord.modal.interaction.HolonetFeatureSubmittedModalAction;
import de.medo.holonetbridge.commons.discord.modal.interaction.HolonetSetupConnectionSubmitModalAction;
import de.medo.holonetbridge.commons.discord.ticketing.HolonetTicketCreationSessionStore;
import de.medo.holonetbridge.commons.domain.HolonetReportFormData;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicketKind;
import de.medo.holonetbridge.commons.service.HolonetBotInteractionSessionService;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.modals.Modal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataAccessResourceFailureException;

class HolonetReportFormInteractionTest {
    private final HolonetBotInteractionSessionService dataService = mock(HolonetBotInteractionSessionService.class);
    private final HolonetTicketSubmitter submitter = mock(HolonetTicketSubmitter.class);
    private final org.springframework.context.MessageSource messages =
            new MessageSourceConfiguration().messageSource(new I18nProperties(null));

    @ParameterizedTest
    @CsvSource({"BUG", "FEATURE"})
    void buttonsLoadDurableDraftEvenWithoutInMemorySession(HolonetTicketKind kind) {
        var event = mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);
        when(event.getGuild().getIdLong()).thenReturn(1L);
        when(event.getUser().getIdLong()).thenReturn(2L);
        when(event.getChannel().getIdLong()).thenReturn(3L);
        when(event.getUserLocale()).thenReturn(DiscordLocale.GERMAN);
        var data = new HolonetReportFormData();
        data.setTitle("Saved title");
        data.setDescription("Saved description");
        data.setSteps("Saved steps");
        data.setExpected("Saved expected");
        data.setActual("Saved actual");
        data.setRecurrence("Jedes Mal");
        when(dataService.getOrCreate(1, 2, 3, Locale.GERMAN, kind)).thenReturn(data);
        var factory = new HolonetTicketReportModalFactory(messages);
        var sessions = new HolonetTicketCreationSessionStore();

        if (kind == HolonetTicketKind.BUG) {
            new HolonetBugReportModalButton(messages, sessions, factory, dataService).handle(event);
        } else {
            new HolonetFeatureModalButton(messages, sessions, factory, dataService).handle(event);
        }

        var modal = org.mockito.ArgumentCaptor.forClass(Modal.class);
        verify(event).replyModal(modal.capture());
        var components = modal.getValue().toData().getArray("components");
        assertThat(components.getObject(0).getObject("component").getString("value")).isEqualTo("Saved title");
        if (kind == HolonetTicketKind.BUG) {
            assertThat(modal.getValue().getId()).isEqualTo(HolonetTicketReportModalFactory.MODAL_ID_BUG_REPORT);
            assertThat(components.getObject(1).getObject("component").getString("value")).isEqualTo("Saved steps");
            assertThat(components.getObject(2).getObject("component").getString("value")).isEqualTo("Saved expected");
            assertThat(components.getObject(3).getObject("component").getString("value")).isEqualTo("Saved actual");
            assertThat(components.getObject(4).getObject("component").getArray("options").getObject(0).getBoolean("default"))
                    .isTrue();
        } else {
            assertThat(modal.getValue().getId()).isEqualTo(HolonetTicketReportModalFactory.MODAL_ID_FEATURE_REPORT);
            assertThat(components.getObject(1).getObject("component").getString("value")).isEqualTo("Saved description");
        }
    }

    @Test
    void receivedFeatureIsSavedBeforeFurtherProcessingAndSurvivesItsFailure() {
        var event = featureEvent();
        var data = new HolonetReportFormData();
        when(dataService.getOrCreate(1, 2, 3, Locale.US, HolonetTicketKind.FEATURE)).thenReturn(data);
        doThrow(new IllegalStateException("Later processing failed"))
                .when(submitter).submit(eq(event), eq(2L), eq(HolonetTicketKind.FEATURE), any());

        assertThatThrownBy(() -> new HolonetFeatureSubmittedModalAction(submitter, dataService, messages).handle(event))
                .isInstanceOf(IllegalStateException.class);

        var order = inOrder(dataService, submitter);
        order.verify(dataService).save(data);
        order.verify(submitter).submit(eq(event), eq(2L), eq(HolonetTicketKind.FEATURE), any());
        assertThat(data.getTitle()).isEqualTo(" Title ");
        assertThat(data.getDescription()).isEqualTo("  Original\n**text**  ");
    }

    @Test
    void saveFailureDoesNotContinueToTicketProcessing() {
        var event = featureEvent();
        var data = new HolonetReportFormData();
        when(dataService.getOrCreate(1, 2, 3, Locale.US, HolonetTicketKind.FEATURE)).thenReturn(data);
        doThrow(new DataAccessResourceFailureException("unavailable")).when(dataService).save(data);

        new HolonetFeatureSubmittedModalAction(submitter, dataService, messages).handle(event);

        verifyNoInteractions(submitter);
        verify(event).reply(messages.getMessage("ticket.draft-save-failed", null, Locale.US));
    }

    @Test
    void loadingFailureDoesNotOpenAnEmptyModal() {
        var event = mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);
        when(event.getUserLocale()).thenReturn(DiscordLocale.ENGLISH_US);
        when(dataService.getOrCreate(anyLong(), anyLong(), anyLong(), any(), any()))
                .thenThrow(new DataAccessResourceFailureException("unavailable"));
        new HolonetFeatureModalButton(messages, new HolonetTicketCreationSessionStore(),
                new HolonetTicketReportModalFactory(messages), dataService).handle(event);

        verify(event, never()).replyModal(any());
        verify(event).reply(messages.getMessage("ticket.draft-load-failed", null, Locale.US));
    }

    @Test
    void guildOnlyInteractionsDoNotAccessDrafts() {
        var event = featureEvent();
        when(event.getGuild()).thenReturn(null);
        new HolonetFeatureSubmittedModalAction(submitter, dataService, messages).handle(event);
        verifyNoInteractions(dataService, submitter);
        verify(event).reply(messages.getMessage("support.guild-only", null, Locale.US));
    }

    @ParameterizedTest
    @CsvSource({"ticket:modal:bug-report, BUG", "ticket:modal:feature, FEATURE"})
    void routesCanonicalModalIds(String modalId, HolonetTicketKind kind) {
        var event = mock(ModalInteractionEvent.class);
        when(event.getModalId()).thenReturn(modalId);
        var bug = mock(HolonetBugReportSubmitModalAction.class);
        var feature = mock(HolonetFeatureSubmittedModalAction.class);
        new HolonetOnModalInteractionListener(feature, bug, mock(HolonetSetupConnectionSubmitModalAction.class))
                .onModalInteraction(event);
        if (kind == HolonetTicketKind.BUG) {
            verify(bug).handle(event);
            verifyNoInteractions(feature);
        } else {
            verify(feature).handle(event);
            verifyNoInteractions(bug);
        }
    }

    @Test
    void currentlyDisabledProviderOnlyAcknowledgesSavedDraft() {
        var event = featureEvent();
        new HolonetTicketSubmitter(messages, mock(HolonetTicketCreationSessionStore.class),
                mock(de.medo.holonetbridge.commons.discord.ticketing.HolonetSupportChannelRepository.class))
                .submit(event, 2, HolonetTicketKind.FEATURE, null);
        verify(event).reply(messages.getMessage("ticket.draft-saved", null, Locale.US));
    }

    private ModalInteractionEvent featureEvent() {
        var event = mock(ModalInteractionEvent.class, RETURNS_DEEP_STUBS);
        when(event.getGuild().getIdLong()).thenReturn(1L);
        when(event.getUser().getIdLong()).thenReturn(2L);
        when(event.getChannel().getIdLong()).thenReturn(3L);
        when(event.getUserLocale()).thenReturn(DiscordLocale.ENGLISH_US);
        when(event.getValue("title").getAsString()).thenReturn(" Title ");
        when(event.getValue("description").getAsString()).thenReturn("  Original\n**text**  ");
        return event;
    }
}
