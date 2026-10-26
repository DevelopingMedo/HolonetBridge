package de.medo.holonetbridge.commons.discord.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.medo.holonetbridge.commons.discord.modal.HolonetBugReportSubmitModalAction;
import de.medo.holonetbridge.commons.discord.modal.HolonetFeatureSubmittedModalAction;
import de.medo.holonetbridge.commons.discord.modal.HolonetTicketSubmitter;
import de.medo.holonetbridge.commons.discord.ticketing.HolonetSupportChannel;
import de.medo.holonetbridge.commons.discord.ticketing.HolonetSupportChannelRepository;
import de.medo.holonetbridge.commons.discord.ticketing.HolonetTicketCreationSessionStore;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicketKind;
import de.medo.holonetbridge.commons.extapi.TicketDto;
import de.medo.holonetbridge.commons.extapi.TicketProvider;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Answers;

/**
 * Drives {@link HolonetOnModalInteractionListener} through its modal submissions using Mockito mocks of the JDA
 * event types directly, without any real gateway connection.
 */
class HolonetOnModalInteractionListenerTest {

    private static final long USER_ID = 123456789L;
    private static final long CHANNEL_ID = 111L;

    private final HolonetTicketCreationSessionStore sessionStore = new HolonetTicketCreationSessionStore();
    private final TicketProvider ticketProvider = mock(TicketProvider.class);
    private final HolonetSupportChannelRepository supportChannelRepository =
            mock(HolonetSupportChannelRepository.class);
    private final HolonetOnModalInteractionListener listener = new HolonetOnModalInteractionListener(
            new HolonetFeatureSubmittedModalAction(
                    new HolonetTicketSubmitter(sessionStore, ticketProvider, supportChannelRepository)),
            new HolonetBugReportSubmitModalAction(
                    new HolonetTicketSubmitter(sessionStore, ticketProvider, supportChannelRepository)));

    private final User user = mock(User.class);

    @BeforeEach
    void stubUserId() {
        when(user.getIdLong()).thenReturn(USER_ID);
    }

    @Test
    void bugReportModalSubmissionCreatesABugTicketAndPostsTheConfirmationInTheChannel() throws InterruptedException {
        sessionStore.startOrReset(USER_ID).kind(HolonetTicketKind.BUG);

        ModalInteractionEvent event = bugReportEvent("Login fails", "Did X then Y", "It should work", "It crashed");
        InteractionHook hook = stubDeferReplyWithHook(event);
        CountDownLatch hookReplied = stubHookSendMessage(hook);
        TextChannel channel = stubModalChannel(event);
        when(channel.sendMessage(anyString())).thenReturn(mock(MessageCreateAction.class, Answers.RETURNS_SELF));
        when(ticketProvider.create(eq("[Bug] Login fails"), anyString()))
                .thenReturn(new TicketDto("42", "[Bug] Login fails", "ignored"));

        listener.onModalInteraction(event);
        awaitHookReply(hookReplied);

        ArgumentCaptor<String> descriptionCaptor = ArgumentCaptor.forClass(String.class);
        verify(ticketProvider).create(eq("[Bug] Login fails"), descriptionCaptor.capture());
        assertThat(descriptionCaptor.getValue())
                .isEqualTo("## Was hast du vor dem Fehler gemacht?\n\nDid X then Y\n\n"
                        + "## Was hättest du erwartet?\n\nIt should work\n\n"
                        + "## Was ist stattdessen passiert?\n\nIt crashed\n\n"
                        + "## Tritt der Fehler erneut auf?\n\nJedes Mal");
        verify(channel).sendMessage("Ticket #42 wurde angelegt: [Bug] Login fails");
        verify(hook).sendMessage("Ticket #42 wurde angelegt: [Bug] Login fails");
        verify(event, never()).reply(anyString());
        assertThat(sessionStore.get(USER_ID)).isEmpty();
    }

    @Test
    void featureModalSubmissionCreatesAFeatureTicketAndPostsTheConfirmationInTheChannel() throws InterruptedException {
        sessionStore.startOrReset(USER_ID).kind(HolonetTicketKind.FEATURE);

        ModalInteractionEvent event = mock(ModalInteractionEvent.class);
        when(event.getModalId()).thenReturn("ticket:modal:feature");
        when(event.getUser()).thenReturn(user);
        ModalMapping title = textMapping("Dark mode");
        ModalMapping description = textMapping("I want a dark theme toggle in settings.");
        when(event.getValue("title")).thenReturn(title);
        when(event.getValue("description")).thenReturn(description);
        InteractionHook hook = stubDeferReplyWithHook(event);
        CountDownLatch hookReplied = stubHookSendMessage(hook);
        TextChannel channel = stubModalChannel(event);
        when(channel.sendMessage(anyString())).thenReturn(mock(MessageCreateAction.class, Answers.RETURNS_SELF));
        when(ticketProvider.create(eq("[Feature] Dark mode"), anyString()))
                .thenReturn(new TicketDto("7", "[Feature] Dark mode", "I want a dark theme toggle in settings."));

        listener.onModalInteraction(event);
        awaitHookReply(hookReplied);

        verify(ticketProvider).create("[Feature] Dark mode", "I want a dark theme toggle in settings.");
        verify(channel).sendMessage("Ticket #7 wurde angelegt: [Feature] Dark mode");
        verify(hook).sendMessage("Ticket #7 wurde angelegt: [Feature] Dark mode");
        assertThat(sessionStore.get(USER_ID)).isEmpty();
    }

    @Test
    void twoConsecutiveBugReportSubmissionsEachCompleteTheirOwnDeferredReplyAndDoNotLeaveTheUserWaiting()
            throws InterruptedException {
        sessionStore.startOrReset(USER_ID).kind(HolonetTicketKind.BUG);
        ModalInteractionEvent firstEvent =
                bugReportEvent("First bug", "did A", "expected A", "got B");
        InteractionHook firstHook = stubDeferReplyWithHook(firstEvent);
        CountDownLatch firstHookReplied = stubHookSendMessage(firstHook);
        TextChannel firstChannel = stubModalChannel(firstEvent);
        when(firstChannel.sendMessage(anyString())).thenReturn(mock(MessageCreateAction.class, Answers.RETURNS_SELF));
        when(ticketProvider.create(eq("[Bug] First bug"), anyString()))
                .thenReturn(new TicketDto("1", "[Bug] First bug", "ignored"));

        listener.onModalInteraction(firstEvent);
        awaitHookReply(firstHookReplied);

        verify(firstHook).sendMessage("Ticket #1 wurde angelegt: [Bug] First bug");
        assertThat(sessionStore.get(USER_ID)).isEmpty();

        // Same user reports a second, independent bug right after the first one finished.
        sessionStore.startOrReset(USER_ID).kind(HolonetTicketKind.BUG);
        ModalInteractionEvent secondEvent =
                bugReportEvent("Second bug", "did C", "expected C", "got D");
        InteractionHook secondHook = stubDeferReplyWithHook(secondEvent);
        CountDownLatch secondHookReplied = stubHookSendMessage(secondHook);
        TextChannel secondChannel = stubModalChannel(secondEvent);
        when(secondChannel.sendMessage(anyString()))
                .thenReturn(mock(MessageCreateAction.class, Answers.RETURNS_SELF));
        when(ticketProvider.create(eq("[Bug] Second bug"), anyString()))
                .thenReturn(new TicketDto("2", "[Bug] Second bug", "ignored"));

        listener.onModalInteraction(secondEvent);
        awaitHookReply(secondHookReplied);

        verify(secondHook).sendMessage("Ticket #2 wurde angelegt: [Bug] Second bug");
        assertThat(sessionStore.get(USER_ID)).isEmpty();
        // Each interaction is acknowledged exactly once - no second deferReply/reply on either event.
        verify(firstEvent).deferReply(true);
        verify(secondEvent).deferReply(true);
        verify(firstEvent, never()).reply(anyString());
        verify(secondEvent, never()).reply(anyString());
    }

    @Test
    void providerExceptionDuringTicketCreationCompletesTheDeferredReplyWithAnErrorInsteadOfLeavingItOpen()
            throws InterruptedException {
        sessionStore.startOrReset(USER_ID).kind(HolonetTicketKind.BUG);
        ModalInteractionEvent event =
                bugReportEvent("Login fails", "did A", "expected A", "got B");
        InteractionHook hook = stubDeferReplyWithHook(event);
        CountDownLatch hookReplied = stubHookSendMessage(hook);
        TextChannel channel = stubModalChannel(event);
        when(ticketProvider.create(eq("[Bug] Login fails"), anyString()))
                .thenThrow(new RuntimeException("provider unreachable"));

        listener.onModalInteraction(event);
        awaitHookReply(hookReplied);

        ArgumentCaptor<String> hookMessageCaptor = ArgumentCaptor.forClass(String.class);
        verify(hook).sendMessage(hookMessageCaptor.capture());
        assertThat(hookMessageCaptor.getValue())
                .doesNotContain("provider unreachable")
                .contains("konnte nicht angelegt werden");
        verify(channel, never()).sendMessage(anyString());
        verify(event, never()).reply(anyString());
        verify(event).deferReply(true);
        // The session survives a failed attempt so the user can simply resubmit without restarting the flow.
        assertThat(sessionStore.get(USER_ID)).isPresent();
    }

    @Test
    void failureToLinkTheCreatedTicketToItsSupportChannelStillCompletesTheDeferredReplyWithAnError()
            throws InterruptedException {
        sessionStore.startOrReset(USER_ID).kind(HolonetTicketKind.BUG);
        ModalInteractionEvent event =
                bugReportEvent("Login fails", "did A", "expected A", "got B");
        InteractionHook hook = stubDeferReplyWithHook(event);
        CountDownLatch hookReplied = stubHookSendMessage(hook);
        TextChannel channel = stubModalChannel(event);
        when(ticketProvider.create(eq("[Bug] Login fails"), anyString()))
                .thenReturn(new TicketDto("42", "[Bug] Login fails", "ignored"));
        when(supportChannelRepository.findById(CHANNEL_ID))
                .thenReturn(Optional.of(new HolonetSupportChannel(CHANNEL_ID, 1L, USER_ID, 1L)));
        when(supportChannelRepository.save(any())).thenThrow(new RuntimeException("database unavailable"));

        listener.onModalInteraction(event);
        awaitHookReply(hookReplied);

        ArgumentCaptor<String> hookMessageCaptor = ArgumentCaptor.forClass(String.class);
        verify(hook).sendMessage(hookMessageCaptor.capture());
        assertThat(hookMessageCaptor.getValue())
                .doesNotContain("database unavailable")
                .contains("konnte nicht angelegt werden");
        verify(channel, never()).sendMessage(anyString());
        verify(event).deferReply(true);
        verify(event, never()).reply(anyString());
        assertThat(sessionStore.get(USER_ID)).isPresent();
    }

    @Test
    void bugReportModalSubmissionWithoutASessionAsksToRestart() {
        ModalInteractionEvent event = mock(ModalInteractionEvent.class);
        when(event.getModalId()).thenReturn("ticket:modal:bug-report");
        when(event.getUser()).thenReturn(user);
        ModalMapping title = textMapping("Login fails");
        ModalMapping steps = textMapping("x");
        ModalMapping expected = textMapping("x");
        ModalMapping actual = textMapping("x");
        ModalMapping recurrence = selectionMapping("Jedes Mal");
        when(event.getValue("title")).thenReturn(title);
        when(event.getValue("steps")).thenReturn(steps);
        when(event.getValue("expected")).thenReturn(expected);
        when(event.getValue("actual")).thenReturn(actual);
        when(event.getValue("recurrence")).thenReturn(recurrence);
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class, Answers.RETURNS_SELF);
        when(event.reply(anyString())).thenReturn(replyAction);

        listener.onModalInteraction(event);

        verify(event).reply("Die Sitzung ist abgelaufen, bitte klick erneut auf „Support anfragen“.");
        verify(ticketProvider, never()).create(anyString(), anyString());
    }

    @Test
    void featureModalSubmissionWithoutASessionAsksToRestart() {
        ModalInteractionEvent event = mock(ModalInteractionEvent.class);
        when(event.getModalId()).thenReturn("ticket:modal:feature");
        when(event.getUser()).thenReturn(user);
        ModalMapping title = textMapping("Dark mode");
        ModalMapping description = textMapping("description");
        when(event.getValue("title")).thenReturn(title);
        when(event.getValue("description")).thenReturn(description);
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class, Answers.RETURNS_SELF);
        when(event.reply(anyString())).thenReturn(replyAction);

        listener.onModalInteraction(event);

        verify(event).reply("Die Sitzung ist abgelaufen, bitte klick erneut auf „Support anfragen“.");
        verify(ticketProvider, never()).create(anyString(), anyString());
    }

    @Test
    void unknownModalIdsAreIgnored() {
        ModalInteractionEvent event = mock(ModalInteractionEvent.class);
        when(event.getModalId()).thenReturn("something:else");

        listener.onModalInteraction(event);

        verify(event, never()).reply(anyString());
        verify(event, never()).deferReply(anyBoolean());
    }

    /**
     * Builds a bug-report modal event stubbed with the given title and the three free-text report fields. The
     * {@link ModalMapping} mocks are built up front, not inline inside {@code thenReturn(...)}: nesting a second
     * {@code when(...)} call before the outer one is completed corrupts Mockito's ongoing-stubbing state.
     */
    private ModalInteractionEvent bugReportEvent(String title, String steps, String expected, String actual) {
        ModalInteractionEvent event = mock(ModalInteractionEvent.class);
        when(event.getModalId()).thenReturn("ticket:modal:bug-report");
        when(event.getUser()).thenReturn(user);
        ModalMapping titleMapping = textMapping(title);
        ModalMapping stepsMapping = textMapping(steps);
        ModalMapping expectedMapping = textMapping(expected);
        ModalMapping actualMapping = textMapping(actual);
        ModalMapping recurrenceMapping = selectionMapping("Jedes Mal");
        when(event.getValue("title")).thenReturn(titleMapping);
        when(event.getValue("steps")).thenReturn(stepsMapping);
        when(event.getValue("expected")).thenReturn(expectedMapping);
        when(event.getValue("actual")).thenReturn(actualMapping);
        when(event.getValue("recurrence")).thenReturn(recurrenceMapping);
        return event;
    }

    /**
     * Stubs {@code event.deferReply(true)} so its queued success callback is actually invoked with a mock
     * {@link InteractionHook}, since the ticket submitter now does the provider call and every reply after that
     * callback runs - a plain {@code RETURNS_SELF} mock would silently never run it and hide a stuck interaction.
     */
    private static InteractionHook stubDeferReplyWithHook(ModalInteractionEvent event) {
        InteractionHook hook = mock(InteractionHook.class);
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class, Answers.RETURNS_SELF);
        when(event.deferReply(true)).thenReturn(replyAction);
        doAnswer(invocation -> {
            Consumer<InteractionHook> onHook = invocation.getArgument(0);
            onHook.accept(hook);
            return null;
        }).when(replyAction).queue(any());
        return hook;
    }

    /**
     * Stubs {@code hook.sendMessage(...)} to count down the returned latch once its queue callback runs. The
     * ticket submitter finishes the provider call on a separate thread, so tests must wait for this signal instead
     * of asserting right after {@code onModalInteraction} returns.
     */
    private static CountDownLatch stubHookSendMessage(InteractionHook hook) {
        CountDownLatch latch = new CountDownLatch(1);
        WebhookMessageCreateAction action = mock(WebhookMessageCreateAction.class, Answers.RETURNS_SELF);
        when(hook.sendMessage(anyString())).thenReturn(action);
        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(action).queue(any(), any());
        return latch;
    }

    private static void awaitHookReply(CountDownLatch hookReplied) throws InterruptedException {
        assertThat(hookReplied.await(5, TimeUnit.SECONDS))
                .as("deferred reply must be completed via the InteractionHook")
                .isTrue();
    }

    /** Stubs the modal event's channel (union + text channel) and returns the {@link TextChannel}. */
    private static TextChannel stubModalChannel(ModalInteractionEvent event) {
        MessageChannelUnion union = mock(MessageChannelUnion.class);
        when(event.getChannel()).thenReturn(union);
        when(union.getIdLong()).thenReturn(CHANNEL_ID);
        TextChannel channel = mock(TextChannel.class);
        when(union.asTextChannel()).thenReturn(channel);
        return channel;
    }

    /** A {@link ModalMapping} whose {@code getAsString()} returns the given text. */
    private static ModalMapping textMapping(String value) {
        ModalMapping mapping = mock(ModalMapping.class);
        when(mapping.getAsString()).thenReturn(value);
        return mapping;
    }

    /** A {@link ModalMapping} whose {@code getAsStringList()} returns the single chosen select value. */
    private static ModalMapping selectionMapping(String value) {
        ModalMapping mapping = mock(ModalMapping.class);
        when(mapping.getAsStringList()).thenReturn(List.of(value));
        return mapping;
    }
}
