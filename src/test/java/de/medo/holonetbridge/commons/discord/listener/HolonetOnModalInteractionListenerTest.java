package de.medo.holonetbridge.commons.discord.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import de.medo.holonetbridge.commons.crypto.EncryptedSecret;
import de.medo.holonetbridge.commons.discord.i18n.I18nProperties;
import de.medo.holonetbridge.commons.discord.i18n.Texts;
import de.medo.holonetbridge.commons.discord.i18n.TicketDescriptionFormatter;
import de.medo.holonetbridge.commons.discord.modal.HolonetBugReportSubmitModalAction;
import de.medo.holonetbridge.commons.discord.modal.HolonetFeatureSubmittedModalAction;
import de.medo.holonetbridge.commons.discord.modal.HolonetSetupConnectionSubmitModalAction;
import de.medo.holonetbridge.commons.discord.modal.HolonetTicketSubmitter;
import de.medo.holonetbridge.commons.discord.ticketing.HolonetSupportChannel;
import de.medo.holonetbridge.commons.discord.ticketing.HolonetSupportChannelRepository;
import de.medo.holonetbridge.commons.discord.ticketing.HolonetSupportChannelStatus;
import de.medo.holonetbridge.commons.discord.ticketing.HolonetTicketCreationSessionStore;
import de.medo.holonetbridge.commons.extapi.TicketDto;
import de.medo.holonetbridge.commons.extapi.TicketProvider;
import de.medo.holonetbridge.commons.provider.HolonetProviderConnection;
import de.medo.holonetbridge.commons.provider.HolonetProviderType;
import de.medo.holonetbridge.commons.provider.ProviderConnectionResolver;
import de.medo.holonetbridge.commons.provider.ResolvedProvider;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;

/**
 * Drives {@link HolonetOnModalInteractionListener} through its modal submissions using Mockito mocks of the JDA
 * event types directly, without any real gateway connection.
 */
class HolonetOnModalInteractionListenerTest {

    private final Texts texts = new Texts(new I18nProperties(null));

    private static final long USER_ID = 123456789L;
    private static final long GUILD_ID = 999L;
    private static final long CHANNEL_ID = 111L;
    private static final long OTHER_CHANNEL_ID = 222L;
    private static final String PROVIDER_BASE_URL = "https://tickets.example.invalid";
    private static final UUID CONNECTION_ID = UUID.randomUUID();

    private final HolonetTicketCreationSessionStore sessionStore = new HolonetTicketCreationSessionStore();
    private final TicketProvider ticketProvider = mock(TicketProvider.class);
    private final HolonetSupportChannelRepository supportChannelRepository =
            mock(HolonetSupportChannelRepository.class);
    private final ProviderConnectionResolver connectionResolver = mock(ProviderConnectionResolver.class);
    private final HolonetProviderConnection connection = new HolonetProviderConnection(
            CONNECTION_ID,
            GUILD_ID,
            HolonetProviderType.GITEA,
            PROVIDER_BASE_URL,
            "acme",
            "support",
            true,
            new EncryptedSecret(new byte[] {1}, new byte[] {2}, "v1"),
            Instant.now(),
            Instant.now());
    private final HolonetSetupConnectionSubmitModalAction setupConnectionSubmit =
            mock(HolonetSetupConnectionSubmitModalAction.class);
    private final HolonetOnModalInteractionListener listener = new HolonetOnModalInteractionListener(
            new HolonetFeatureSubmittedModalAction(
                    new HolonetTicketSubmitter(texts, new TicketDescriptionFormatter(texts), sessionStore, connectionResolver, supportChannelRepository)),
            new HolonetBugReportSubmitModalAction(
                    new HolonetTicketSubmitter(texts, new TicketDescriptionFormatter(texts), sessionStore, connectionResolver, supportChannelRepository)),
            setupConnectionSubmit);

    private final User user = mock(User.class);

    @BeforeEach
    void stubDefaults() {
        when(user.getIdLong()).thenReturn(USER_ID);
        // Default: the channel is tracked and has no ticket yet, and the guild has a resolvable connection.
        // Individual tests override either stub to exercise the guards that depend on them.
        when(supportChannelRepository.findById(CHANNEL_ID))
                .thenReturn(Optional.of(new HolonetSupportChannel(CHANNEL_ID, GUILD_ID, USER_ID, 1L)));
        when(connectionResolver.forGuild(GUILD_ID)).thenReturn(Optional.of(new ResolvedProvider(connection, ticketProvider)));
    }

    @Test
    void bugReportModalSubmissionCreatesABugTicketAndPostsTheConfirmationInTheChannel() throws InterruptedException {
        sessionStore.startOrReset(GUILD_ID, CHANNEL_ID, USER_ID);
        ModalInteractionEvent event = bugReportEvent("Login fails", "Did X then Y", "It should work", "It crashed");
        InteractionHook hook = stubDeferReplyWithHook(event);
        CountDownLatch hookReplied = stubHookSendMessage(hook);
        TextChannel channel = stubModalChannel(event, CHANNEL_ID);
        when(channel.sendMessage(anyString())).thenReturn(mock(MessageCreateAction.class, Answers.RETURNS_SELF));
        when(ticketProvider.create(eq("[Bug] Login fails"), anyString()))
                .thenReturn(new TicketDto(
                        "42", "[Bug] Login fails", "ignored", PROVIDER_BASE_URL + "/acme/support/issues/42"));

        listener.onModalInteraction(event);
        awaitHookReply(hookReplied);

        ArgumentCaptor<String> descriptionCaptor = ArgumentCaptor.forClass(String.class);
        verify(ticketProvider).create(eq("[Bug] Login fails"), descriptionCaptor.capture());
        assertThat(descriptionCaptor.getValue())
                .isEqualTo("## Was hast du vor dem Fehler gemacht?\n\nDid X then Y\n\n"
                        + "## Was hättest du erwartet?\n\nIt should work\n\n"
                        + "## Was ist stattdessen passiert?\n\nIt crashed\n\n"
                        + "## Tritt der Fehler erneut auf?\n\nJedes Mal");
        String expectedConfirmation = "Ticket #42 wurde angelegt: [Bug] Login fails\n"
                + "Ticket öffnen: " + PROVIDER_BASE_URL + "/acme/support/issues/42";
        verify(channel).sendMessage(expectedConfirmation);
        verify(hook).sendMessage(expectedConfirmation);
        verify(event, never()).reply(anyString());
        assertThat(sessionStore.get(GUILD_ID, CHANNEL_ID, USER_ID)).isEmpty();
    }

    @Test
    void usesServerLanguageForTicketAndPublicMessageButUserLanguageForPersonalReply() throws InterruptedException {
        ModalInteractionEvent event = bugReportEvent("Login fails", "Did X", "Works", "Crashed");
        when(event.getUserLocale()).thenReturn(DiscordLocale.CROATIAN);
        ModalMapping recurrence = selectionMapping("always");
        when(event.getValue("recurrence")).thenReturn(recurrence);
        InteractionHook hook = stubDeferReplyWithHook(event);
        CountDownLatch replied = stubHookSendMessage(hook);
        TextChannel channel = stubModalChannel(event, CHANNEL_ID);
        when(channel.sendMessage(anyString())).thenReturn(mock(MessageCreateAction.class, Answers.RETURNS_SELF));
        String url = PROVIDER_BASE_URL + "/acme/support/issues/42";
        when(ticketProvider.create(eq("[Bug] Login fails"), anyString()))
                .thenReturn(new TicketDto("42", "[Bug] Login fails", "ignored", url));

        listener.onModalInteraction(event);
        awaitHookReply(replied);

        ArgumentCaptor<String> description = ArgumentCaptor.forClass(String.class);
        verify(ticketProvider).create(eq("[Bug] Login fails"), description.capture());
        assertThat(description.getValue()).startsWith("## Was hast du vor dem Fehler gemacht?").endsWith("Jedes Mal");
        verify(channel).sendMessage("Ticket #42 wurde angelegt: [Bug] Login fails\nTicket öffnen: " + url);
        verify(hook).sendMessage("Prijava #42 je otvorena: [Bug] Login fails\nOtvori prijavu: " + url);
    }

    @Test
    void featureModalSubmissionCreatesAFeatureTicketAndPostsTheConfirmationInTheChannel() throws InterruptedException {
        sessionStore.startOrReset(GUILD_ID, CHANNEL_ID, USER_ID);
        ModalInteractionEvent event = mock(ModalInteractionEvent.class);
        when(event.getUserLocale()).thenReturn(DiscordLocale.GERMAN);
        when(event.getGuildLocale()).thenReturn(DiscordLocale.GERMAN);
        when(event.getModalId()).thenReturn("ticket:modal:feature");
        when(event.getUser()).thenReturn(user);
        ModalMapping title = textMapping("Dark mode");
        ModalMapping description = textMapping("I want a dark theme toggle in settings.");
        when(event.getValue("title")).thenReturn(title);
        when(event.getValue("description")).thenReturn(description);
        InteractionHook hook = stubDeferReplyWithHook(event);
        CountDownLatch hookReplied = stubHookSendMessage(hook);
        TextChannel channel = stubModalChannel(event, CHANNEL_ID);
        when(channel.sendMessage(anyString())).thenReturn(mock(MessageCreateAction.class, Answers.RETURNS_SELF));
        when(ticketProvider.create(eq("[Feature] Dark mode"), anyString()))
                .thenReturn(new TicketDto(
                        "7",
                        "[Feature] Dark mode",
                        "I want a dark theme toggle in settings.",
                        PROVIDER_BASE_URL + "/acme/support/issues/7"));

        listener.onModalInteraction(event);
        awaitHookReply(hookReplied);

        verify(ticketProvider).create("[Feature] Dark mode", "I want a dark theme toggle in settings.");
        String expectedConfirmation = "Ticket #7 wurde angelegt: [Feature] Dark mode\n"
                + "Ticket öffnen: " + PROVIDER_BASE_URL + "/acme/support/issues/7";
        verify(channel).sendMessage(expectedConfirmation);
        verify(hook).sendMessage(expectedConfirmation);
        assertThat(sessionStore.get(GUILD_ID, CHANNEL_ID, USER_ID)).isEmpty();
    }

    /**
     * Two different channels (each with their own support-channel row, matching "one channel = at most one
     * ticket") are submitted right after each other; each must get its own correct confirmation without
     * interfering with the other's deferred reply.
     */
    @Test
    void twoConsecutiveBugReportSubmissionsFromDifferentChannelsEachCompleteTheirOwnDeferredReplyIndependently()
            throws InterruptedException {
        when(supportChannelRepository.findById(OTHER_CHANNEL_ID))
                .thenReturn(Optional.of(new HolonetSupportChannel(OTHER_CHANNEL_ID, GUILD_ID, USER_ID, 2L)));
        ModalInteractionEvent firstEvent =
                bugReportEvent("First bug", "did A", "expected A", "got B");
        InteractionHook firstHook = stubDeferReplyWithHook(firstEvent);
        CountDownLatch firstHookReplied = stubHookSendMessage(firstHook);
        TextChannel firstChannel = stubModalChannel(firstEvent, CHANNEL_ID);
        when(firstChannel.sendMessage(anyString())).thenReturn(mock(MessageCreateAction.class, Answers.RETURNS_SELF));
        when(ticketProvider.create(eq("[Bug] First bug"), anyString()))
                .thenReturn(new TicketDto(
                        "1", "[Bug] First bug", "ignored", PROVIDER_BASE_URL + "/acme/support/issues/1"));

        listener.onModalInteraction(firstEvent);
        awaitHookReply(firstHookReplied);

        verify(firstHook)
                .sendMessage("Ticket #1 wurde angelegt: [Bug] First bug\n"
                        + "Ticket öffnen: " + PROVIDER_BASE_URL + "/acme/support/issues/1");

        ModalInteractionEvent secondEvent =
                bugReportEvent("Second bug", "did C", "expected C", "got D");
        InteractionHook secondHook = stubDeferReplyWithHook(secondEvent);
        CountDownLatch secondHookReplied = stubHookSendMessage(secondHook);
        TextChannel secondChannel = stubModalChannel(secondEvent, OTHER_CHANNEL_ID);
        when(secondChannel.sendMessage(anyString()))
                .thenReturn(mock(MessageCreateAction.class, Answers.RETURNS_SELF));
        when(ticketProvider.create(eq("[Bug] Second bug"), anyString()))
                .thenReturn(new TicketDto(
                        "2", "[Bug] Second bug", "ignored", PROVIDER_BASE_URL + "/acme/support/issues/2"));

        listener.onModalInteraction(secondEvent);
        awaitHookReply(secondHookReplied);

        verify(secondHook)
                .sendMessage("Ticket #2 wurde angelegt: [Bug] Second bug\n"
                        + "Ticket öffnen: " + PROVIDER_BASE_URL + "/acme/support/issues/2");
        // Each interaction is acknowledged exactly once - no second deferReply/reply on either event.
        verify(firstEvent).deferReply(true);
        verify(secondEvent).deferReply(true);
        verify(firstEvent, never()).reply(anyString());
        verify(secondEvent, never()).reply(anyString());
    }

    @Test
    void ticketWithAMissingUrlStillConfirmsOnceWithoutRecreatingTheTicket() throws InterruptedException {
        ModalInteractionEvent event = bugReportEvent("Login fails", "did A", "expected A", "got B");
        InteractionHook hook = stubDeferReplyWithHook(event);
        CountDownLatch hookReplied = stubHookSendMessage(hook);
        TextChannel channel = stubModalChannel(event, CHANNEL_ID);
        when(channel.sendMessage(anyString())).thenReturn(mock(MessageCreateAction.class, Answers.RETURNS_SELF));
        when(ticketProvider.create(eq("[Bug] Login fails"), anyString()))
                .thenReturn(new TicketDto("42", "[Bug] Login fails", "ignored", null));

        listener.onModalInteraction(event);
        awaitHookReply(hookReplied);

        String expectedConfirmation = "Ticket #42 wurde angelegt: [Bug] Login fails\n"
                + "(Link derzeit nicht verfügbar.)";
        verify(hook).sendMessage(expectedConfirmation);
        verify(channel).sendMessage(expectedConfirmation);
        verify(ticketProvider, times(1)).create(anyString(), anyString());
    }

    @Test
    void ticketWithAUrlOnAForeignHostIsTreatedAsMissingInsteadOfBeingShown() throws InterruptedException {
        ModalInteractionEvent event = bugReportEvent("Login fails", "did A", "expected A", "got B");
        InteractionHook hook = stubDeferReplyWithHook(event);
        CountDownLatch hookReplied = stubHookSendMessage(hook);
        TextChannel channel = stubModalChannel(event, CHANNEL_ID);
        when(channel.sendMessage(anyString())).thenReturn(mock(MessageCreateAction.class, Answers.RETURNS_SELF));
        when(ticketProvider.create(eq("[Bug] Login fails"), anyString()))
                .thenReturn(new TicketDto(
                        "42", "[Bug] Login fails", "ignored", "https://attacker.invalid/phish/42"));

        listener.onModalInteraction(event);
        awaitHookReply(hookReplied);

        String expectedConfirmation = "Ticket #42 wurde angelegt: [Bug] Login fails\n"
                + "(Link derzeit nicht verfügbar.)";
        verify(hook).sendMessage(expectedConfirmation);
        verify(channel).sendMessage(expectedConfirmation);
        verify(ticketProvider, times(1)).create(anyString(), anyString());
    }

    /**
     * Regression guard for a provider adapter whose ticket identities are not Gitea-style numbers: the
     * submitter must treat {@link TicketDto#id()} as an opaque string throughout.
     */
    @Test
    void ticketWithAnAlphanumericIdFromAnotherProviderIsConfirmedWithoutAnyGiteaSpecificFormatAssumption()
            throws InterruptedException {
        ModalInteractionEvent event = bugReportEvent("Login fails", "did A", "expected A", "got B");
        InteractionHook hook = stubDeferReplyWithHook(event);
        CountDownLatch hookReplied = stubHookSendMessage(hook);
        TextChannel channel = stubModalChannel(event, CHANNEL_ID);
        when(channel.sendMessage(anyString())).thenReturn(mock(MessageCreateAction.class, Answers.RETURNS_SELF));
        when(ticketProvider.create(eq("[Bug] Login fails"), anyString()))
                .thenReturn(new TicketDto(
                        "PROJ-42x",
                        "[Bug] Login fails",
                        "ignored",
                        PROVIDER_BASE_URL + "/browse/PROJ-42x"));

        listener.onModalInteraction(event);
        awaitHookReply(hookReplied);

        String expectedConfirmation = "Ticket #PROJ-42x wurde angelegt: [Bug] Login fails\n"
                + "Ticket öffnen: " + PROVIDER_BASE_URL + "/browse/PROJ-42x";
        verify(hook).sendMessage(expectedConfirmation);
        verify(channel).sendMessage(expectedConfirmation);
    }

    @Test
    void providerExceptionDuringTicketCreationCompletesTheDeferredReplyWithAnErrorInsteadOfLeavingItOpen()
            throws InterruptedException {
        ModalInteractionEvent event =
                bugReportEvent("Login fails", "did A", "expected A", "got B");
        InteractionHook hook = stubDeferReplyWithHook(event);
        CountDownLatch hookReplied = stubHookSendMessage(hook);
        stubModalChannel(event, CHANNEL_ID);
        when(ticketProvider.create(eq("[Bug] Login fails"), anyString()))
                .thenThrow(new RuntimeException("provider unreachable"));

        listener.onModalInteraction(event);
        awaitHookReply(hookReplied);

        ArgumentCaptor<String> hookMessageCaptor = ArgumentCaptor.forClass(String.class);
        verify(hook).sendMessage(hookMessageCaptor.capture());
        assertThat(hookMessageCaptor.getValue())
                .doesNotContain("provider unreachable")
                .contains("konnte nicht angelegt werden");
        verify(event, never()).reply(anyString());
        verify(event).deferReply(true);
        // No support-channel state is touched at all when the provider call itself fails.
        verify(supportChannelRepository, never()).save(any());
    }

    @Test
    void failureToLinkTheCreatedTicketToItsSupportChannelStillCompletesTheDeferredReplyWithAnError()
            throws InterruptedException {
        ModalInteractionEvent event =
                bugReportEvent("Login fails", "did A", "expected A", "got B");
        InteractionHook hook = stubDeferReplyWithHook(event);
        CountDownLatch hookReplied = stubHookSendMessage(hook);
        TextChannel channel = stubModalChannel(event, CHANNEL_ID);
        when(ticketProvider.create(eq("[Bug] Login fails"), anyString()))
                .thenReturn(new TicketDto(
                        "42", "[Bug] Login fails", "ignored", PROVIDER_BASE_URL + "/acme/support/issues/42"));
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
    }

    @Test
    void bugReportSubmissionForAnUntrackedChannelRepliesWithoutCallingTheProvider() {
        when(supportChannelRepository.findById(CHANNEL_ID)).thenReturn(Optional.empty());
        ModalInteractionEvent event = bugReportEvent("Login fails", "x", "x", "x");
        MessageChannelUnion union = channelUnion(CHANNEL_ID);
        when(event.getChannel()).thenReturn(union);
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class, Answers.RETURNS_SELF);
        when(event.reply(anyString())).thenReturn(replyAction);

        listener.onModalInteraction(event);

        verify(event).reply("Dieser Kanal wird nicht als Support-Kanal geführt. "
                + "Bitte starte eine neue Anfrage über „Support anfragen“.");
        verify(event, never()).deferReply(anyBoolean());
        verify(ticketProvider, never()).create(anyString(), anyString());
    }

    @Test
    void bugReportSubmissionWhenTheChannelAlreadyHasATicketRepliesWithoutCreatingASecondTicket() {
        HolonetSupportChannel alreadyTicketed = new HolonetSupportChannel(CHANNEL_ID, GUILD_ID, USER_ID, 1L);
        alreadyTicketed.ticketCreated(CONNECTION_ID, "99", PROVIDER_BASE_URL + "/acme/support/issues/99");
        when(supportChannelRepository.findById(CHANNEL_ID)).thenReturn(Optional.of(alreadyTicketed));
        ModalInteractionEvent event = bugReportEvent("Login fails", "x", "x", "x");
        MessageChannelUnion union = channelUnion(CHANNEL_ID);
        when(event.getChannel()).thenReturn(union);
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class, Answers.RETURNS_SELF);
        when(event.reply(anyString())).thenReturn(replyAction);

        listener.onModalInteraction(event);

        verify(event).reply("Für diesen Kanal wurde bereits ein Ticket angelegt.");
        verify(event, never()).deferReply(anyBoolean());
        verify(ticketProvider, never()).create(anyString(), anyString());
    }

    @Test
    void bugReportSubmissionWhenNoProviderConnectionIsResolvedForTheGuildMarksTheChannelBrokenAndReplies()
            throws InterruptedException {
        when(connectionResolver.forGuild(GUILD_ID)).thenReturn(Optional.empty());
        ModalInteractionEvent event = bugReportEvent("Login fails", "x", "x", "x");
        InteractionHook hook = stubDeferReplyWithHook(event);
        CountDownLatch hookReplied = stubHookSendMessage(hook);
        stubModalChannel(event, CHANNEL_ID);

        listener.onModalInteraction(event);
        awaitHookReply(hookReplied);

        verify(hook).sendMessage("Für diesen Server ist noch kein Ticket-Provider eingerichtet. "
                + "Ein Serveradmin muss zuerst /setup ausführen.");
        verify(ticketProvider, never()).create(anyString(), anyString());
        ArgumentCaptor<HolonetSupportChannel> savedChannel = ArgumentCaptor.forClass(HolonetSupportChannel.class);
        verify(supportChannelRepository).save(savedChannel.capture());
        assertThat(savedChannel.getValue().status()).isEqualTo(HolonetSupportChannelStatus.BROKEN);
    }

    @Test
    void aConfirmedDiscordDeliveryFailureWhilePostingIntoTheChannelMarksItBrokenEvenThoughTheTicketWasCreated()
            throws InterruptedException {
        ModalInteractionEvent event = bugReportEvent("Login fails", "x", "x", "x");
        InteractionHook hook = stubDeferReplyWithHook(event);
        CountDownLatch hookReplied = stubHookSendMessage(hook);
        TextChannel channel = stubModalChannel(event, CHANNEL_ID);
        MessageCreateAction channelSendAction = mock(MessageCreateAction.class, Answers.RETURNS_SELF);
        when(channel.sendMessage(anyString())).thenReturn(channelSendAction);
        ErrorResponseException deliveryFailure = mock(ErrorResponseException.class);
        when(deliveryFailure.getErrorResponse()).thenReturn(ErrorResponse.UNKNOWN_CHANNEL);
        doAnswer(invocation -> {
            Consumer<Throwable> onFailure = invocation.getArgument(1);
            onFailure.accept(deliveryFailure);
            return null;
        }).when(channelSendAction).queue(any(), any());
        when(ticketProvider.create(eq("[Bug] Login fails"), anyString()))
                .thenReturn(new TicketDto(
                        "42", "[Bug] Login fails", "ignored", PROVIDER_BASE_URL + "/acme/support/issues/42"));

        listener.onModalInteraction(event);
        awaitHookReply(hookReplied);

        ArgumentCaptor<HolonetSupportChannel> savedChannel = ArgumentCaptor.forClass(HolonetSupportChannel.class);
        verify(supportChannelRepository, times(2)).save(savedChannel.capture());
        List<HolonetSupportChannel> savedValues = savedChannel.getAllValues();
        assertThat(savedValues.get(savedValues.size() - 1).status()).isEqualTo(HolonetSupportChannelStatus.BROKEN);
        assertThat(savedValues.get(savedValues.size() - 1).ticketId()).isEqualTo("42");
    }

    @Test
    void unknownModalIdsAreIgnored() {
        ModalInteractionEvent event = mock(ModalInteractionEvent.class);
        when(event.getUserLocale()).thenReturn(DiscordLocale.GERMAN);
        when(event.getGuildLocale()).thenReturn(DiscordLocale.GERMAN);
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
        when(event.getUserLocale()).thenReturn(DiscordLocale.GERMAN);
        when(event.getGuildLocale()).thenReturn(DiscordLocale.GERMAN);
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
        when(hook.getInteraction()).thenReturn(event);
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

    /** Stubs the modal event's guild and channel (union + text channel) and returns the {@link TextChannel}. */
    private static TextChannel stubModalChannel(ModalInteractionEvent event, long channelId) {
        Guild guild = mock(Guild.class);
        when(guild.getLocale()).thenReturn(DiscordLocale.GERMAN);
        when(guild.getIdLong()).thenReturn(GUILD_ID);
        when(event.getGuild()).thenReturn(guild);
        MessageChannelUnion union = channelUnion(channelId);
        when(event.getChannel()).thenReturn(union);
        TextChannel channel = mock(TextChannel.class);
        when(union.asTextChannel()).thenReturn(channel);
        return channel;
    }

    private static MessageChannelUnion channelUnion(long channelId) {
        MessageChannelUnion union = mock(MessageChannelUnion.class);
        when(union.getIdLong()).thenReturn(channelId);
        return union;
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
