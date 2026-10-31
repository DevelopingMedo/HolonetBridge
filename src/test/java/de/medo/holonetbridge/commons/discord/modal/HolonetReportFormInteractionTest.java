package de.medo.holonetbridge.commons.discord.modal;

import de.holonetbridge.commons.message.SupportMessageCodes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Locale;

import de.medo.holonetbridge.commons.discord.button.HolonetReportBugButton;
import de.medo.holonetbridge.commons.discord.button.HolonetReportFeatureButton;
import de.medo.holonetbridge.commons.discord.i18n.I18nProperties;
import de.medo.holonetbridge.commons.discord.i18n.MessageSourceConfiguration;
import de.medo.holonetbridge.commons.discord.listener.HolonetOnModalInteractionListener;
import de.medo.holonetbridge.commons.discord.modal.interaction.HolonetBugReportSubmitModalAction;
import de.medo.holonetbridge.commons.discord.modal.interaction.HolonetFeatureSubmittedModalAction;
import de.medo.holonetbridge.commons.discord.modal.interaction.HolonetSetupConnectionSubmitModalAction;
import de.medo.holonetbridge.commons.discord.ticketing.HolonetTicketInteractionGuard;
import de.medo.holonetbridge.commons.discord.ticketing.HolonetChannelCoordinator;
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
    private final HolonetTicketInteractionGuard guard = mock(HolonetTicketInteractionGuard.class);
    @org.junit.jupiter.api.BeforeEach
    void allowOpenChannel() { when(guard.allowCreate(any())).thenReturn(true); }
    private final HolonetBotInteractionSessionService dataService = mock(HolonetBotInteractionSessionService.class);
    private final HolonetTicketSubmitter submitter = mock(HolonetTicketSubmitter.class);
    private final org.springframework.context.MessageSource messages =
            new MessageSourceConfiguration().messageSource(new I18nProperties(null));

    @ParameterizedTest
    @CsvSource({"BUG", "FEATURE"})
    void buttonsLoadDurableDraftAndReplyWithTheMatchingModal(HolonetTicketKind kind) {
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
        when(dataService.getOrCreate(1, 2, Locale.GERMAN, kind)).thenReturn(data);
        var factory = new HolonetTicketReportModalFactory(messages);

        if (kind == HolonetTicketKind.BUG) {
            new HolonetReportBugButton(messages, factory, dataService, guard).handle(event);
        } else {
            new HolonetReportFeatureButton(messages, factory, dataService, guard).handle(event);
        }

        var modal = org.mockito.ArgumentCaptor.forClass(Modal.class);
        verify(event).replyModal(modal.capture());
        verify(event, never()).reply(anyString());
        verify(event, never()).deferReply(anyBoolean());
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
        when(dataService.getOrCreate(1, 2, Locale.US, HolonetTicketKind.FEATURE)).thenReturn(data);
        doThrow(new IllegalStateException("Later processing failed"))
                .when(submitter).handle(event, data);

        assertThatThrownBy(() -> new HolonetFeatureSubmittedModalAction(submitter, dataService, messages, guard).handle(event))
                .isInstanceOf(IllegalStateException.class);

        var order = inOrder(dataService, submitter);
        order.verify(dataService).save(data);
        order.verify(submitter).handle(event, data);
        assertThat(data.getTitle()).isEqualTo(" Title ");
        assertThat(data.getDescription()).isEqualTo("  Original\n**text**  ");
    }

    @Test
    void saveFailureDoesNotContinueToTicketProcessing() {
        var event = featureEvent();
        var data = new HolonetReportFormData();
        when(dataService.getOrCreate(1, 2, Locale.US, HolonetTicketKind.FEATURE)).thenReturn(data);
        doThrow(new DataAccessResourceFailureException("unavailable")).when(dataService).save(data);

        new HolonetFeatureSubmittedModalAction(submitter, dataService, messages, guard).handle(event);

        verifyNoInteractions(submitter);
        verify(event).reply(messages.getMessage("ticket.draft-save-failed", null, Locale.US));
    }

    @ParameterizedTest
    @CsvSource({"BUG", "FEATURE"})
    void loadingFailureDoesNotOpenAnEmptyModal(HolonetTicketKind kind) {
        var event = mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);
        when(event.getUserLocale()).thenReturn(DiscordLocale.ENGLISH_US);
        when(dataService.getOrCreate(anyLong(), anyLong(), any(), any()))
                .thenThrow(new DataAccessResourceFailureException("unavailable"));
        var factory = new HolonetTicketReportModalFactory(messages);
        if (kind == HolonetTicketKind.BUG) {
            new HolonetReportBugButton(messages, factory, dataService, guard).handle(event);
        } else {
            new HolonetReportFeatureButton(messages, factory, dataService, guard).handle(event);
        }

        verify(event, never()).replyModal(any());
        verify(event).reply(messages.getMessage(SupportMessageCodes.REPORT_DRAFT_LOAD_FAILURE, null, Locale.US));
    }

    @Test
    void nonGuildModalsAreIgnoredBeforeAccessingFormsOrReplying() {
        var event = mock(ModalInteractionEvent.class);
        var setup = mock(HolonetSetupConnectionSubmitModalAction.class);
        var listener = new HolonetOnModalInteractionListener(
                new HolonetFeatureSubmittedModalAction(submitter, dataService, messages, guard),
                new HolonetBugReportSubmitModalAction(submitter, messages, dataService, guard), setup);

        listener.onModalInteraction(event);

        verify(event).isFromGuild();
        verifyNoMoreInteractions(event);
        verifyNoInteractions(guard, dataService, submitter, setup);
    }

    @ParameterizedTest
    @CsvSource({"ticket:modal:bug-report, BUG", "ticket:modal:feature, FEATURE"})
    void routesCanonicalModalIds(String modalId, HolonetTicketKind kind) {
        var event = mock(ModalInteractionEvent.class);
        when(event.isFromGuild()).thenReturn(true);
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

    @ParameterizedTest
    @CsvSource({"BUG", "FEATURE"})
    void bothModalsSaveDraftAndCreateExactlyOneExternalTicket(HolonetTicketKind kind) {
        var event = featureEvent();
        when(event.getModalId()).thenReturn(kind == HolonetTicketKind.BUG
                ? HolonetTicketReportModalFactory.MODAL_ID_BUG_REPORT
                : HolonetTicketReportModalFactory.MODAL_ID_FEATURE_REPORT);
        when(event.getValue("recurrence").getAsStringList()).thenReturn(java.util.List.of("always"));
        var data = new HolonetReportFormData();
        when(dataService.getOrCreate(1, 2, Locale.US, kind)).thenReturn(data);
        var session = new de.medo.holonetbridge.commons.domain.HolonetBotInteractionSession();
        session.setGuildId(1L);
        session.setMemberId(2L);
        data.setSession(session);
        var providers = mock(de.medo.holonetbridge.commons.provider.HolonetTicketProviderService.class);
        var provider = mock(de.medo.holonetbridge.commons.extapi.TicketProvider.class);
        when(providers.providerFor(1L)).thenReturn(java.util.Optional.of(provider));
        when(provider.create(data)).thenReturn(new de.medo.holonetbridge.commons.extapi.TicketReference(
                "gitea", "https://gitea.example.invalid", "acme", "support", 11L, 101L, 3L));
        var hook = mock(net.dv8tion.jda.api.interactions.InteractionHook.class, RETURNS_DEEP_STUBS);
        var reply = event.deferReply(true);
        doAnswer(invocation -> {
            java.util.function.Consumer<net.dv8tion.jda.api.interactions.InteractionHook> callback = invocation.getArgument(0);
            callback.accept(hook);
            return null;
        }).when(reply).queue(any());
        when(guard.canAccess(event)).thenReturn(true);
        var realSubmitter = new HolonetTicketSubmitter(providers, messages, new HolonetChannelCoordinator(), guard);
        var listener = new HolonetOnModalInteractionListener(
                new HolonetFeatureSubmittedModalAction(realSubmitter, dataService, messages, guard),
                new HolonetBugReportSubmitModalAction(realSubmitter, messages, dataService, guard),
                mock(HolonetSetupConnectionSubmitModalAction.class));

        listener.onModalInteraction(event);

        verify(hook, timeout(3000)).editOriginal(messages.getMessage("ticket.created", null, Locale.US));
        verify(provider, times(1)).create(data);
        var order = inOrder(dataService, event, provider);
        order.verify(dataService).save(data);
        order.verify(event).deferReply(true);
        order.verify(provider).create(data);
    }

    private ModalInteractionEvent featureEvent() {
        var event = mock(ModalInteractionEvent.class, RETURNS_DEEP_STUBS);
        when(event.isFromGuild()).thenReturn(true);
        when(event.getGuild().getIdLong()).thenReturn(1L);
        when(event.getUser().getIdLong()).thenReturn(2L);
        when(event.getChannel().getIdLong()).thenReturn(3L);
        when(event.getUserLocale()).thenReturn(DiscordLocale.ENGLISH_US);
        when(event.getValue("title").getAsString()).thenReturn(" Title ");
        when(event.getValue("description").getAsString()).thenReturn("  Original\n**text**  ");
        return event;
    }
}
