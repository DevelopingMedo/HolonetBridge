package de.medo.holonetbridge.commons.discord.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import de.medo.holonetbridge.commons.crypto.EncryptedSecret;
import de.medo.holonetbridge.commons.crypto.TokenCryptoService;
import de.medo.holonetbridge.commons.crypto.TokenEncryptionContext;
import de.medo.holonetbridge.commons.discord.button.HolonetSetupRetryButton;
import de.medo.holonetbridge.commons.discord.modal.HolonetProviderConnectionModal;
import de.medo.holonetbridge.commons.discord.modal.HolonetSetupConnectionSubmitModalAction;
import de.medo.holonetbridge.commons.discord.setup.HolonetDiscordServer;
import de.medo.holonetbridge.commons.discord.setup.HolonetDiscordServerRepository;
import de.medo.holonetbridge.commons.discord.setup.HolonetSetupSessionStore;
import de.medo.holonetbridge.commons.domain.setup.HolonetSetupConnectionModal;
import de.medo.holonetbridge.commons.provider.HolonetProviderConnection;
import de.medo.holonetbridge.commons.provider.HolonetProviderConnectionService;
import de.medo.holonetbridge.commons.provider.HolonetProviderType;
import de.medo.holonetbridge.gitea.GiteaConnectionValidationException;
import de.medo.holonetbridge.gitea.GiteaConnectionValidator;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.SelfMember;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.MessageEditAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ModalCallbackAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;

/**
 * Drives {@link HolonetSetupCommandAction} and {@link HolonetSetupConnectionSubmitModalAction} together
 * (sharing one {@link HolonetSetupSessionStore} mock backed by an in-memory map, the same pattern the
 * production listeners use for wiring) through the full {@code /setup} flow: the resume-or-restart prompt,
 * opening the modal, submitting it, and every validation failure that must leave a guild's previously
 * working connection untouched.
 *
 * <p>{@link HolonetSetupSessionStore}'s own persistence correctness (upsert-reuse, discard, survival across
 * an {@code EntityManager} flush/clear) is covered separately by its own {@code @DataJpaTest}; this class
 * only needs the store's documented contract, not a real database.
 */
class HolonetSetupCommandActionTest {

    private static final long USER_ID = 42L;
    private static final long GUILD_ID = 100L;
    private static final long PANEL_CHANNEL_ID = 200L;
    private static final long MESSAGE_ID = 300L;
    private static final String BASE_URL = "https://gitea.example.invalid";
    private static final String OWNER = "acme";
    private static final String REPOSITORY = "support";
    private static final String TOKEN = "secret-token-value";

    private final Map<String, HolonetSetupConnectionModal> drafts = new HashMap<>();
    private final HolonetSetupSessionStore sessionStore = newSessionStoreFake(drafts);
    private final HolonetProviderConnectionService providerConnectionService =
            mock(HolonetProviderConnectionService.class);
    private final HolonetDiscordServerRepository discordServerRepository =
            mock(HolonetDiscordServerRepository.class);
    private final TokenCryptoService tokenCryptoService = mock(TokenCryptoService.class);
    private final GiteaConnectionValidator connectionValidator = mock(GiteaConnectionValidator.class);

    private final HolonetSetupCommandAction commandAction =
            new HolonetSetupCommandAction(sessionStore, providerConnectionService);
    private final HolonetSetupConnectionSubmitModalAction submitAction = new HolonetSetupConnectionSubmitModalAction(
            sessionStore, providerConnectionService, discordServerRepository, tokenCryptoService, connectionValidator);

    // ---------------------------------------------------------------------
    // /setup command: permission/channel gates, resume-or-restart prompt, modal opening
    // ---------------------------------------------------------------------

    @Test
    void setupCommandOpensTheProviderConnectionModalDirectlyWhenNoUnfinishedAttemptExists() {
        when(providerConnectionService.activeConnection(GUILD_ID)).thenReturn(Optional.empty());
        SlashCommandInteractionEvent event = commandEvent(true);
        ModalCallbackAction modalAction = mock(ModalCallbackAction.class, Answers.RETURNS_SELF);
        when(event.replyModal(any(Modal.class))).thenReturn(modalAction);

        commandAction.handle(event);

        ArgumentCaptor<Modal> modalCaptor = ArgumentCaptor.forClass(Modal.class);
        verify(event).replyModal(modalCaptor.capture());
        assertThat(modalCaptor.getValue().getId()).isEqualTo(HolonetProviderConnectionModal.MODAL_ID);
        verify(modalAction).queue();
        verify(event, never()).deferReply(org.mockito.ArgumentMatchers.anyBoolean());
        verify(event, never()).reply(anyString());
        assertThat(drafts).isEmpty();
    }

    @Test
    void setupCommandAsksToResumeOrRestartWhenAnUnfinishedAttemptAlreadyExists() {
        startUnfinishedAttempt();
        SlashCommandInteractionEvent event = commandEvent(true);
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class, Answers.RETURNS_SELF);
        when(event.reply(anyString())).thenReturn(replyAction);

        commandAction.handle(event);

        verify(event).reply("Du hast einen unvollendeten Vorgang. Möchtest du fortsetzen oder neu starten?");
        verify(replyAction).setEphemeral(true);
        verify(replyAction).addComponents(any(ActionRow.class));
        verify(replyAction).queue();
        verify(event, never()).replyModal(any());
    }

    @Test
    void setupCommandRejectsAUserWithoutManageServerPermission() {
        SlashCommandInteractionEvent event = commandEvent(false);
        stubPlainReply(event);

        commandAction.handle(event);

        verify(event).reply("Dazu fehlt dir die Berechtigung \"Server verwalten\".");
        verify(event, never()).replyModal(any());
        verifyNoInteractions(sessionStore);
    }

    // ---------------------------------------------------------------------
    // Full successful flow, first-time setup (new panel) and re-setup (edit in place)
    // ---------------------------------------------------------------------

    @Test
    void modalSubmissionActivatesTheConnectionAndPostsANewPanelOnFirstSetup() throws InterruptedException {
        ModalInteractionEvent event = submissionEvent(BASE_URL, OWNER, REPOSITORY, TOKEN);
        InteractionHook hook = stubDeferReplyWithHook(event);
        stubHookSendMessagePlain(hook);
        TextChannel panelChannel = stubAccessiblePanelChannel(event);
        when(discordServerRepository.findById(GUILD_ID)).thenReturn(Optional.empty());

        MessageCreateAction sendAction = mock(MessageCreateAction.class, Answers.RETURNS_SELF);
        when(panelChannel.sendMessage(anyString())).thenReturn(sendAction);
        CountDownLatch panelSendAttempted = stubPanelSendLatch(sendAction);

        submitAction.handle(event);
        awaitLatch(panelSendAttempted);

        verify(providerConnectionService)
                .activate(GUILD_ID, HolonetProviderType.GITEA, BASE_URL, OWNER, REPOSITORY, TOKEN);
        verify(connectionValidator).verifyReadAccess(URI.create(BASE_URL), TOKEN, OWNER, REPOSITORY);
        assertThat(drafts).doesNotContainKey(key(GUILD_ID, USER_ID));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<Message>> successCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(sendAction).queue(successCaptor.capture(), any());
        Message message = mock(Message.class);
        when(message.getIdLong()).thenReturn(MESSAGE_ID);
        successCaptor.getValue().accept(message);

        ArgumentCaptor<HolonetDiscordServer> savedCaptor = ArgumentCaptor.forClass(HolonetDiscordServer.class);
        verify(discordServerRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().guildId()).isEqualTo(GUILD_ID);
        assertThat(savedCaptor.getValue().channelId()).isEqualTo(PANEL_CHANNEL_ID);
        assertThat(savedCaptor.getValue().messageId()).isEqualTo(MESSAGE_ID);
    }

    @Test
    void modalSubmissionOnReSetupEditsTheExistingPanelMessageInsteadOfPostingADuplicate() throws InterruptedException {
        ModalInteractionEvent event = submissionEvent(BASE_URL, OWNER, REPOSITORY, TOKEN);
        InteractionHook hook = stubDeferReplyWithHook(event);
        stubHookSendMessagePlain(hook);
        TextChannel panelChannel = stubAccessiblePanelChannel(event);
        when(discordServerRepository.findById(GUILD_ID))
                .thenReturn(Optional.of(new HolonetDiscordServer(GUILD_ID, PANEL_CHANNEL_ID, MESSAGE_ID)));

        MessageEditAction editAction = mock(MessageEditAction.class, Answers.RETURNS_SELF);
        when(panelChannel.editMessageById(eq(MESSAGE_ID), anyString())).thenReturn(editAction);
        CountDownLatch editAttempted = stubPanelEditLatch(editAction);

        submitAction.handle(event);
        awaitLatch(editAttempted);

        verify(providerConnectionService)
                .activate(GUILD_ID, HolonetProviderType.GITEA, BASE_URL, OWNER, REPOSITORY, TOKEN);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<Message>> successCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(editAction).queue(successCaptor.capture(), any());
        successCaptor.getValue().accept(mock(Message.class));

        verify(panelChannel, never()).sendMessage(anyString());
        verify(discordServerRepository, never()).save(any());
    }

    // ---------------------------------------------------------------------
    // Two guilds must never mix up their connections or drafts
    // ---------------------------------------------------------------------

    @Test
    void modalSubmissionsForTwoDifferentGuildsActivateIndependentConnectionsWithoutCrossTalk()
            throws InterruptedException {
        long otherUserId = USER_ID + 1;
        long otherGuildId = GUILD_ID + 1;
        long otherPanelChannelId = PANEL_CHANNEL_ID + 1;
        String otherBaseUrl = "https://other-gitea.example.invalid";

        ModalInteractionEvent firstEvent = submissionEvent(BASE_URL, OWNER, REPOSITORY, TOKEN);
        InteractionHook firstHook = stubDeferReplyWithHook(firstEvent);
        stubHookSendMessagePlain(firstHook);
        TextChannel firstPanelChannel = stubAccessiblePanelChannel(firstEvent);
        when(discordServerRepository.findById(GUILD_ID)).thenReturn(Optional.empty());
        MessageCreateAction firstSendAction = mock(MessageCreateAction.class, Answers.RETURNS_SELF);
        when(firstPanelChannel.sendMessage(anyString())).thenReturn(firstSendAction);
        CountDownLatch firstPanelSendAttempted = stubPanelSendLatch(firstSendAction);

        submitAction.handle(firstEvent);
        awaitLatch(firstPanelSendAttempted);
        simulateAsyncPanelPostSuccess(firstSendAction);

        ModalInteractionEvent secondEvent =
                submissionEvent(otherUserId, otherPanelChannelId, otherBaseUrl, OWNER, REPOSITORY, TOKEN);
        InteractionHook secondHook = stubDeferReplyWithHook(secondEvent);
        stubHookSendMessagePlain(secondHook);
        TextChannel secondPanelChannel = stubAccessiblePanelChannel(secondEvent, otherGuildId, otherPanelChannelId);
        when(discordServerRepository.findById(otherGuildId)).thenReturn(Optional.empty());
        MessageCreateAction secondSendAction = mock(MessageCreateAction.class, Answers.RETURNS_SELF);
        when(secondPanelChannel.sendMessage(anyString())).thenReturn(secondSendAction);
        CountDownLatch secondPanelSendAttempted = stubPanelSendLatch(secondSendAction);

        submitAction.handle(secondEvent);
        awaitLatch(secondPanelSendAttempted);
        simulateAsyncPanelPostSuccess(secondSendAction);

        verify(providerConnectionService)
                .activate(GUILD_ID, HolonetProviderType.GITEA, BASE_URL, OWNER, REPOSITORY, TOKEN);
        verify(providerConnectionService)
                .activate(otherGuildId, HolonetProviderType.GITEA, otherBaseUrl, OWNER, REPOSITORY, TOKEN);
        assertThat(drafts).doesNotContainKeys(key(GUILD_ID, USER_ID), key(otherGuildId, otherUserId));
    }

    // ---------------------------------------------------------------------
    // Validation failures: no activation, no secrets, draft kept for retry
    // ---------------------------------------------------------------------

    @Test
    void invalidTokenValidationFailureReportsAFriendlyErrorWithoutActivatingAndKeepsTheDraft()
            throws InterruptedException {
        assertValidationFailureIsReportedWithoutActivation(
                "Das Token hat keinen Lesezugriff auf dieses Repository (oder ist ungültig).");
    }

    @Test
    void wrongRepositoryValidationFailureReportsAFriendlyErrorWithoutActivatingAndKeepsTheDraft()
            throws InterruptedException {
        assertValidationFailureIsReportedWithoutActivation(
                "Repository nicht gefunden. Bitte Owner und Repository-Namen prüfen.");
    }

    @Test
    void unreachableHostValidationFailureReportsAFriendlyErrorWithoutActivatingAndKeepsTheDraft()
            throws InterruptedException {
        assertValidationFailureIsReportedWithoutActivation(
                "Die Basis-URL ist nicht erreichbar oder wird aus Sicherheitsgründen blockiert.");
    }

    @Test
    void forbiddenTargetValidationFailureReportsAFriendlyErrorWithoutActivatingAndKeepsTheDraft()
            throws InterruptedException {
        assertValidationFailureIsReportedWithoutActivation(
                "Diese Basis-URL darf aus Sicherheitsgründen nicht angesprochen werden.");
    }

    private void assertValidationFailureIsReportedWithoutActivation(String friendlyMessage)
            throws InterruptedException {
        ModalInteractionEvent event = submissionEvent(BASE_URL, OWNER, REPOSITORY, TOKEN);
        InteractionHook hook = stubDeferReplyWithHook(event);
        stubAccessiblePanelChannel(event);
        doAnswer(invocation -> {
                    throw new GiteaConnectionValidationException(friendlyMessage);
                })
                .when(connectionValidator)
                .verifyReadAccess(URI.create(BASE_URL), TOKEN, OWNER, REPOSITORY);

        CountDownLatch finished = stubHookSendMessageLatch(hook);

        submitAction.handle(event);
        awaitLatch(finished);

        verify(providerConnectionService, never()).activate(anyLong(), any(), any(), any(), any(), any());
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(hook).sendMessage(messageCaptor.capture());
        assertThat(messageCaptor.getValue())
                .isEqualTo(friendlyMessage)
                .doesNotContain(TOKEN);
        assertThat(drafts).containsKey(key(GUILD_ID, USER_ID));
    }

    @Test
    void sameTargetBlankTokenKeepsTheExistingSecretAndActivatesWithoutANewToken() throws InterruptedException {
        UUID connectionId = UUID.randomUUID();
        EncryptedSecret storedSecret = new EncryptedSecret(new byte[] {1, 2, 3}, new byte[] {4, 5, 6}, "v1");
        HolonetProviderConnection current = new HolonetProviderConnection(
                connectionId,
                GUILD_ID,
                HolonetProviderType.GITEA,
                BASE_URL,
                OWNER,
                REPOSITORY,
                true,
                storedSecret,
                Instant.EPOCH,
                Instant.EPOCH);
        when(providerConnectionService.activeConnection(GUILD_ID)).thenReturn(Optional.of(current));
        when(tokenCryptoService.decrypt(storedSecret, new TokenEncryptionContext(GUILD_ID, connectionId.toString())))
                .thenReturn(TOKEN);

        ModalInteractionEvent event = submissionEvent(BASE_URL, OWNER, REPOSITORY, "");
        InteractionHook hook = stubDeferReplyWithHook(event);
        stubHookSendMessagePlain(hook);
        TextChannel panelChannel = stubAccessiblePanelChannel(event);
        when(discordServerRepository.findById(GUILD_ID)).thenReturn(Optional.empty());
        MessageCreateAction sendAction = mock(MessageCreateAction.class, Answers.RETURNS_SELF);
        when(panelChannel.sendMessage(anyString())).thenReturn(sendAction);
        CountDownLatch panelSendAttempted = stubPanelSendLatch(sendAction);

        submitAction.handle(event);
        awaitLatch(panelSendAttempted);

        verify(connectionValidator).verifyReadAccess(URI.create(BASE_URL), TOKEN, OWNER, REPOSITORY);
        verify(providerConnectionService)
                .activate(GUILD_ID, HolonetProviderType.GITEA, BASE_URL, OWNER, REPOSITORY, null);
    }

    @Test
    void changingTheTargetWithoutANewTokenFailsValidationWithoutContactingTheProvider() {
        UUID connectionId = UUID.randomUUID();
        EncryptedSecret storedSecret = new EncryptedSecret(new byte[] {1}, new byte[] {2}, "v1");
        HolonetProviderConnection current = new HolonetProviderConnection(
                connectionId,
                GUILD_ID,
                HolonetProviderType.GITEA,
                BASE_URL,
                OWNER,
                REPOSITORY,
                true,
                storedSecret,
                Instant.EPOCH,
                Instant.EPOCH);
        when(providerConnectionService.activeConnection(GUILD_ID)).thenReturn(Optional.of(current));

        ModalInteractionEvent event = submissionEvent(BASE_URL, OWNER, "a-different-repository", "");
        stubAccessiblePanelChannel(event);
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class, Answers.RETURNS_SELF);
        when(event.reply(anyString())).thenReturn(replyAction);

        submitAction.handle(event);

        verify(event)
                .reply("Für eine neue Verbindung oder eine Änderung von Basis-URL, Owner oder Repository ist "
                        + "ein Token erforderlich.");
        verify(event, never()).deferReply(org.mockito.ArgumentMatchers.anyBoolean());
        verify(connectionValidator, never()).verifyReadAccess(any(), any(), any(), any());
        verify(providerConnectionService, never()).activate(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void invalidBaseUrlSyntaxFailsBeforeAnyValidatorOrActivationCall() {
        ModalInteractionEvent event = submissionEvent("not a url", OWNER, REPOSITORY, TOKEN);
        stubAccessiblePanelChannel(event);
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class, Answers.RETURNS_SELF);
        when(event.reply(anyString())).thenReturn(replyAction);

        submitAction.handle(event);

        verify(event).reply("Bitte eine gültige http(s)-Basis-URL angeben.");
        verify(connectionValidator, never()).verifyReadAccess(any(), any(), any(), any());
        verify(providerConnectionService, never()).activate(anyLong(), any(), any(), any(), any(), any());
    }

    // ---------------------------------------------------------------------
    // Authorization edge cases at submit time
    // ---------------------------------------------------------------------

    @Test
    void modalSubmissionRejectsAUserWithoutManageServerPermission() {
        ModalInteractionEvent event = submissionEvent(BASE_URL, OWNER, REPOSITORY, TOKEN);
        Member member = mock(Member.class);
        when(member.hasPermission(Permission.MANAGE_SERVER)).thenReturn(false);
        when(event.getMember()).thenReturn(member);
        when(event.getGuild()).thenReturn(mock(Guild.class));
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class, Answers.RETURNS_SELF);
        when(event.reply(anyString())).thenReturn(replyAction);

        submitAction.handle(event);

        verify(event).reply("Dazu fehlt dir die Berechtigung \"Server verwalten\".");
        verify(replyAction, never()).addComponents(any(net.dv8tion.jda.api.components.actionrow.ActionRow.class));
        verify(providerConnectionService, never()).activate(anyLong(), any(), any(), any(), any(), any());
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /** Pre-populates the fake store as if a previous submission had already been upserted for USER_ID/GUILD_ID. */
    private void startUnfinishedAttempt() {
        sessionStore.upsert(GUILD_ID, USER_ID, PANEL_CHANNEL_ID, BASE_URL, OWNER, REPOSITORY);
    }

    private static String key(long guildId, long userId) {
        return guildId + ":" + userId;
    }

    /**
     * A {@link HolonetSetupSessionStore} mock whose {@code findUnfinished}/{@code upsert}/{@code discard}
     * methods are backed by the given map, so the shared test state behaves like the real repository-backed
     * store without needing a database. {@link HolonetSetupSessionStore}'s own mapping/persistence
     * correctness is verified separately by its {@code @DataJpaTest}.
     */
    private static HolonetSetupSessionStore newSessionStoreFake(Map<String, HolonetSetupConnectionModal> drafts) {
        HolonetSetupSessionStore store = mock(HolonetSetupSessionStore.class);
        when(store.findUnfinished(anyLong(), anyLong()))
                .thenAnswer(invocation -> Optional.ofNullable(
                        drafts.get(key(invocation.getArgument(0), invocation.getArgument(1)))));
        when(store.upsert(anyLong(), anyLong(), anyLong(), any(), any(), any())).thenAnswer(invocation -> {
            long guildId = invocation.getArgument(0);
            long userId = invocation.getArgument(1);
            long channelId = invocation.getArgument(2);
            String baseUrl = invocation.getArgument(3);
            String owner = invocation.getArgument(4);
            String repository = invocation.getArgument(5);
            HolonetSetupConnectionModal modal = drafts.computeIfAbsent(
                    key(guildId, userId), ignored -> new HolonetSetupConnectionModal(UUID.randomUUID()));
            modal.update(channelId, baseUrl, owner, repository);
            return modal;
        });
        doAnswer(invocation -> drafts.remove(key(invocation.getArgument(0), invocation.getArgument(1))))
                .when(store)
                .discard(anyLong(), anyLong());
        return store;
    }

    private SlashCommandInteractionEvent commandEvent(boolean memberAuthorized) {
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        Guild guild = mock(Guild.class);
        when(guild.getIdLong()).thenReturn(GUILD_ID);
        when(event.getGuild()).thenReturn(guild);

        Member member = mock(Member.class);
        when(member.getIdLong()).thenReturn(USER_ID);
        when(member.hasPermission(Permission.MANAGE_SERVER)).thenReturn(memberAuthorized);
        when(event.getMember()).thenReturn(member);

        return event;
    }

    private static void stubPlainReply(SlashCommandInteractionEvent event) {
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class, Answers.RETURNS_SELF);
        when(event.reply(anyString())).thenReturn(replyAction);
    }

    private ModalInteractionEvent submissionEvent(String baseUrl, String owner, String repository, String token) {
        return submissionEvent(USER_ID, PANEL_CHANNEL_ID, baseUrl, owner, repository, token);
    }

    /**
     * Builds the mocks up front, not inline inside {@code thenReturn(...)}: nesting a second {@code when(...)}
     * call before the outer one is completed corrupts Mockito's ongoing-stubbing state.
     */
    private ModalInteractionEvent submissionEvent(
            long userId, long channelId, String baseUrl, String owner, String repository, String token) {
        ModalInteractionEvent event = mock(ModalInteractionEvent.class);
        net.dv8tion.jda.api.entities.User user = userWithId(userId);
        ModalMapping channelMapping = channelMapping(channelId);
        ModalMapping baseUrlMapping = textMapping(baseUrl);
        ModalMapping ownerMapping = textMapping(owner);
        ModalMapping repositoryMapping = textMapping(repository);
        ModalMapping tokenMapping = textMapping(token);
        when(event.getUser()).thenReturn(user);
        when(event.getValue(HolonetProviderConnectionModal.INPUT_CHANNEL)).thenReturn(channelMapping);
        when(event.getValue(HolonetProviderConnectionModal.INPUT_BASE_URL)).thenReturn(baseUrlMapping);
        when(event.getValue(HolonetProviderConnectionModal.INPUT_OWNER)).thenReturn(ownerMapping);
        when(event.getValue(HolonetProviderConnectionModal.INPUT_REPOSITORY)).thenReturn(repositoryMapping);
        when(event.getValue(HolonetProviderConnectionModal.INPUT_TOKEN)).thenReturn(tokenMapping);
        return event;
    }

    private static ModalMapping channelMapping(long channelId) {
        ModalMapping mapping = mock(ModalMapping.class);
        when(mapping.getAsLongList()).thenReturn(java.util.List.of(channelId));
        return mapping;
    }

    private static net.dv8tion.jda.api.entities.User userWithId(long userId) {
        net.dv8tion.jda.api.entities.User user = mock(net.dv8tion.jda.api.entities.User.class);
        when(user.getIdLong()).thenReturn(userId);
        return user;
    }

    private static ModalMapping textMapping(String value) {
        ModalMapping mapping = mock(ModalMapping.class);
        when(mapping.getAsString()).thenReturn(value);
        return mapping;
    }

    private InteractionHook stubDeferReplyWithHook(ModalInteractionEvent event) {
        InteractionHook hook = mock(InteractionHook.class);
        ReplyCallbackAction deferAction = mock(ReplyCallbackAction.class, Answers.RETURNS_SELF);
        when(event.deferReply(true)).thenReturn(deferAction);
        doAnswer(invocation -> {
                    Consumer<InteractionHook> onHook = invocation.getArgument(0);
                    onHook.accept(hook);
                    return null;
                })
                .when(deferAction)
                .queue(any());
        return hook;
    }

    private TextChannel stubAccessiblePanelChannel(ModalInteractionEvent event) {
        return stubAccessiblePanelChannel(event, GUILD_ID, PANEL_CHANNEL_ID);
    }

    private TextChannel stubAccessiblePanelChannel(ModalInteractionEvent event, long guildId, long panelChannelId) {
        Guild guild = mock(Guild.class);
        Member member = mock(Member.class);
        when(guild.getIdLong()).thenReturn(guildId);
        when(member.hasPermission(Permission.MANAGE_SERVER)).thenReturn(true);
        when(event.getGuild()).thenReturn(guild);
        when(event.getMember()).thenReturn(member);

        TextChannel panelChannel = mock(TextChannel.class);
        when(panelChannel.getIdLong()).thenReturn(panelChannelId);
        when(guild.getTextChannelById(panelChannelId)).thenReturn(panelChannel);

        SelfMember selfMember = mock(SelfMember.class);
        when(selfMember.hasPermission(panelChannel, Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND))
                .thenReturn(true);
        when(guild.getSelfMember()).thenReturn(selfMember);

        return panelChannel;
    }

    /**
     * Stubs {@code hook.sendMessage(...)} to count down once its (zero-argument, possibly after a chained
     * {@code addComponents(...)}) {@code queue()} call runs. Used only where that is the single, final async
     * step reached by the validation/activation code (the validation-failure error path); the success path
     * reaches an intermediate panel-channel call first, so it synchronizes on {@link #stubPanelSendLatch} or
     * {@link #stubPanelEditLatch} instead and only needs {@link #stubHookSendMessagePlain} for its final,
     * synchronously-triggered confirmation.
     */
    private static CountDownLatch stubHookSendMessageLatch(InteractionHook hook) {
        CountDownLatch latch = new CountDownLatch(1);
        WebhookMessageCreateAction<Message> action = newHookSendAction();
        when(hook.sendMessage(anyString())).thenReturn(action);
        doAnswer(invocation -> {
                    latch.countDown();
                    return null;
                })
                .when(action)
                .queue();
        return latch;
    }

    /**
     * Stubs {@code hook.sendMessage(...)} with no synchronization: used by the success-path tests, where the
     * confirmation is triggered synchronously (on the test thread) by manually invoking a captured panel-action
     * success callback, so there is nothing left to await by the time this runs.
     */
    private static void stubHookSendMessagePlain(InteractionHook hook) {
        when(hook.sendMessage(anyString())).thenReturn(newHookSendAction());
    }

    @SuppressWarnings("unchecked")
    private static WebhookMessageCreateAction<Message> newHookSendAction() {
        return mock(WebhookMessageCreateAction.class, Answers.RETURNS_SELF);
    }

    /**
     * Counts down once the panel channel's "post a new message" action is actually queued with a
     * success/failure callback - the point the async validation+activation chain has definitely finished and
     * reached {@code postNewPanel}. Mockito never invokes that callback itself, so the caller must capture and
     * invoke it manually afterward to drive the rest of the (then-synchronous) completion path.
     */
    private static CountDownLatch stubPanelSendLatch(MessageCreateAction sendAction) {
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
                    latch.countDown();
                    return null;
                })
                .when(sendAction)
                .queue(any(), any());
        return latch;
    }

    /** Same as {@link #stubPanelSendLatch}, but for the re-setup "edit the existing panel message" action. */
    private static CountDownLatch stubPanelEditLatch(MessageEditAction editAction) {
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
                    latch.countDown();
                    return null;
                })
                .when(editAction)
                .queue(any(), any());
        return latch;
    }

    private static void simulateAsyncPanelPostSuccess(MessageCreateAction sendAction) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<Message>> successCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(sendAction).queue(successCaptor.capture(), any());
        Message message = mock(Message.class);
        when(message.getIdLong()).thenReturn(MESSAGE_ID);
        successCaptor.getValue().accept(message);
    }

    private static void awaitLatch(CountDownLatch latch) throws InterruptedException {
        assertThat(latch.await(5, TimeUnit.SECONDS))
                .as("validation/activation must complete and finish the deferred reply")
                .isTrue();
    }
}
