package de.medo.holonetbridge.commons.discord.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.function.Consumer;

import de.medo.holonetbridge.commons.discord.button.HolonetAddWatcherButton;
import de.medo.holonetbridge.commons.discord.button.HolonetBugReportModalButton;
import de.medo.holonetbridge.commons.discord.button.HolonetFeatureModalButton;
import de.medo.holonetbridge.commons.discord.button.HolonetOpenSupportButton;
import de.medo.holonetbridge.commons.discord.button.HolonetSetupRetryButton;
import de.medo.holonetbridge.commons.discord.i18n.I18nProperties;
import de.medo.holonetbridge.commons.discord.i18n.MessageSourceConfiguration;
import de.medo.holonetbridge.commons.discord.setup.HolonetSetupDraftStore;
import de.medo.holonetbridge.commons.discord.ticketing.HolonetGuildSequenceService;
import de.medo.holonetbridge.commons.discord.ticketing.HolonetSupportChannel;
import de.medo.holonetbridge.commons.discord.ticketing.HolonetSupportChannelRepository;
import de.medo.holonetbridge.commons.discord.ticketing.HolonetTicketCreationSessionStore;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicketKind;
import de.medo.holonetbridge.commons.provider.HolonetProviderConnectionService;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.SelfMember;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.requests.restaction.ChannelAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ModalCallbackAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.springframework.context.MessageSource;

/**
 * Drives {@link HolonetOnButtonInteractionListener} through its four buttons using Mockito mocks of the JDA event
 * types directly, without any real gateway connection.
 *
 * <p>Fluent JDA actions ({@link ReplyCallbackAction}, {@link ModalCallbackAction}, {@link
 * WebhookMessageCreateAction}) are interfaces whose chained setter methods are themselves mocked unless stubbed,
 * so they are mocked with {@link Answers#RETURNS_SELF} to make the {@code .setEphemeral(true).queue()} style
 * chains used in production code work without stubbing every single step.
 */
class HolonetOnButtonInteractionListenerTest {

    private final MessageSource messageSource = new MessageSourceConfiguration().messageSource(new I18nProperties(null));

    private static final long USER_ID = 123456789L;
    private static final long GUILD_ID = 999L;
    private static final long CHANNEL_ID = 111L;

    private final HolonetTicketCreationSessionStore sessionStore = new HolonetTicketCreationSessionStore();
    private final HolonetSupportChannelRepository supportChannelRepository =
            mock(HolonetSupportChannelRepository.class);
    private final HolonetSetupDraftStore setupDraftStore = new HolonetSetupDraftStore();
    private final HolonetProviderConnectionService providerConnectionService =
            mock(HolonetProviderConnectionService.class);
    private final HolonetGuildSequenceService guildSequenceService = mock(HolonetGuildSequenceService.class);
    private final HolonetOnButtonInteractionListener listener = new HolonetOnButtonInteractionListener(
            new HolonetOpenSupportButton(messageSource, sessionStore, supportChannelRepository, guildSequenceService),
            new HolonetBugReportModalButton(messageSource, sessionStore),
            new HolonetFeatureModalButton(messageSource, sessionStore),
            new HolonetAddWatcherButton(messageSource),
            new HolonetSetupRetryButton(messageSource, setupDraftStore, providerConnectionService));

    private final User user = mock(User.class);

    @BeforeEach
    void stubUserId() {
        when(user.getIdLong()).thenReturn(USER_ID);
    }

    @Test
    void supportButtonCreatesAPrivateChannelAndPostsTheKindChooser() {
        Guild guild = mock(Guild.class);
        when(guild.getLocale()).thenReturn(DiscordLocale.GERMAN);
        when(guild.getIdLong()).thenReturn(GUILD_ID);
        when(guildSequenceService.nextNumberFor(GUILD_ID)).thenReturn(1L);
        Member member = mock(Member.class);
        when(member.getIdLong()).thenReturn(USER_ID);
        Role publicRole = mock(Role.class);
        when(guild.getPublicRole()).thenReturn(publicRole);
        when(guild.getSelfMember()).thenReturn(mock(SelfMember.class));

        MessageChannelUnion buttonChannelUnion = mock(MessageChannelUnion.class);
        TextChannel buttonChannel = mock(TextChannel.class);
        when(buttonChannelUnion.asTextChannel()).thenReturn(buttonChannel);
        when(buttonChannel.getParentCategory()).thenReturn(null);

        ChannelAction<TextChannel> channelAction = mock(ChannelAction.class, Answers.RETURNS_SELF);
        when(guild.createTextChannel("holonetbridge-00001", null)).thenReturn(channelAction);

        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);

        when(event.getUserLocale()).thenReturn(DiscordLocale.GERMAN);

        when(event.getGuildLocale()).thenReturn(DiscordLocale.GERMAN);
        when(event.getComponentId()).thenReturn(HolonetOpenSupportButton.BUTTON_ID);
        when(event.getGuild()).thenReturn(guild);
        when(event.getMember()).thenReturn(member);
        when(event.getChannel()).thenReturn(buttonChannelUnion);
        when(event.deferReply(true)).thenReturn(mock(ReplyCallbackAction.class, Answers.RETURNS_SELF));
        InteractionHook hook = mock(InteractionHook.class);
        when(hook.getInteraction()).thenReturn(event);
        when(event.getHook()).thenReturn(hook);
        when(hook.sendMessage(anyString())).thenReturn(newSendMessageAction());

        listener.onButtonInteraction(event);

        verify(guild).createTextChannel("holonetbridge-00001", null);
        verify(channelAction, times(3))
                .addPermissionOverride(any(), nullable(Collection.class), nullable(Collection.class));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<TextChannel>> successCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(channelAction).queue(successCaptor.capture(), any());
        TextChannel createdChannel = mock(TextChannel.class);
        when(createdChannel.getIdLong()).thenReturn(CHANNEL_ID);
        when(createdChannel.getAsMention()).thenReturn("#holonetbridge-00001");
        when(createdChannel.sendMessage(anyString()))
                .thenReturn(mock(MessageCreateAction.class, Answers.RETURNS_SELF));
        successCaptor.getValue().accept(createdChannel);

        ArgumentCaptor<HolonetSupportChannel> channelCaptor = ArgumentCaptor.forClass(HolonetSupportChannel.class);
        verify(supportChannelRepository).save(channelCaptor.capture());
        assertThat(channelCaptor.getValue().channelId()).isEqualTo(CHANNEL_ID);
        assertThat(channelCaptor.getValue().guildId()).isEqualTo(GUILD_ID);
        assertThat(channelCaptor.getValue().requesterUserId()).isEqualTo(USER_ID);
        assertThat(channelCaptor.getValue().number()).isEqualTo(1L);

        assertThat(sessionStore.get(GUILD_ID, CHANNEL_ID, USER_ID)).isPresent();
        verify(createdChannel).sendMessage("Hast du einen Bug gefunden oder hast du eine neue Idee?");
        verify(hook).sendMessage("Support-Kanal erstellt: #holonetbridge-00001");
    }

    @Test
    void bugButtonStoresKindAndOpensTheBugReportModal() {
        sessionStore.startOrReset(GUILD_ID, CHANNEL_ID, USER_ID);
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        when(event.getUserLocale()).thenReturn(DiscordLocale.GERMAN);
        when(event.getGuildLocale()).thenReturn(DiscordLocale.GERMAN);
        when(event.getComponentId()).thenReturn(HolonetBugReportModalButton.BUTTON_ID);
        when(event.getUser()).thenReturn(user);
        stubGuildAndChannel(event);
        ModalCallbackAction modalCallbackAction = mock(ModalCallbackAction.class, Answers.RETURNS_SELF);
        ArgumentCaptor<Modal> modalCaptor = ArgumentCaptor.forClass(Modal.class);
        when(event.replyModal(modalCaptor.capture())).thenReturn(modalCallbackAction);

        listener.onButtonInteraction(event);

        assertThat(sessionStore.get(GUILD_ID, CHANNEL_ID, USER_ID)).hasValueSatisfying(
                session -> assertThat(session.kind()).isEqualTo(HolonetTicketKind.BUG));
        assertThat(modalCaptor.getValue().getId()).isEqualTo(HolonetBugReportModalButton.MODAL_ID);
        verify(modalCallbackAction).queue();
    }

    @Test
    void featureButtonStoresKindAndOpensTheFeatureModal() {
        sessionStore.startOrReset(GUILD_ID, CHANNEL_ID, USER_ID);
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        when(event.getUserLocale()).thenReturn(DiscordLocale.GERMAN);
        when(event.getGuildLocale()).thenReturn(DiscordLocale.GERMAN);
        when(event.getComponentId()).thenReturn(HolonetFeatureModalButton.BUTTON_ID);
        when(event.getUser()).thenReturn(user);
        stubGuildAndChannel(event);
        ModalCallbackAction modalCallbackAction = mock(ModalCallbackAction.class, Answers.RETURNS_SELF);
        ArgumentCaptor<Modal> modalCaptor = ArgumentCaptor.forClass(Modal.class);
        when(event.replyModal(modalCaptor.capture())).thenReturn(modalCallbackAction);

        listener.onButtonInteraction(event);

        assertThat(sessionStore.get(GUILD_ID, CHANNEL_ID, USER_ID)).hasValueSatisfying(
                session -> assertThat(session.kind()).isEqualTo(HolonetTicketKind.FEATURE));
        assertThat(modalCaptor.getValue().getId()).isEqualTo(HolonetFeatureModalButton.MODAL_ID);
        verify(modalCallbackAction).queue();
    }

    /** Stubs the guild/channel id lookups {@code HolonetBugReportModalButton}/{@code HolonetFeatureModalButton} use. */
    private static void stubGuildAndChannel(ButtonInteractionEvent event) {
        Guild guild = mock(Guild.class);
        when(guild.getLocale()).thenReturn(DiscordLocale.GERMAN);
        when(guild.getIdLong()).thenReturn(GUILD_ID);
        when(event.getGuild()).thenReturn(guild);
        MessageChannelUnion channel = mock(MessageChannelUnion.class);
        when(channel.getIdLong()).thenReturn(CHANNEL_ID);
        when(event.getChannel()).thenReturn(channel);
    }

    @Test
    void unknownButtonIdsAreIgnored() {
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        when(event.getUserLocale()).thenReturn(DiscordLocale.GERMAN);
        when(event.getGuildLocale()).thenReturn(DiscordLocale.GERMAN);
        when(event.getComponentId()).thenReturn("something:else");

        listener.onButtonInteraction(event);

        verify(event, never()).reply(anyString());
        verify(event, never()).replyModal(any());
    }

    @Test
    void watcherButtonOpensTheUserSelector() {
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        when(event.getUserLocale()).thenReturn(DiscordLocale.GERMAN);
        when(event.getGuildLocale()).thenReturn(DiscordLocale.GERMAN);
        when(event.getComponentId()).thenReturn(HolonetAddWatcherButton.BUTTON_ID);
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class, Answers.RETURNS_SELF);
        when(event.reply(anyString())).thenReturn(replyAction);

        listener.onButtonInteraction(event);

        verify(event).reply("Wähle eine Person aus, die den Kanal sehen und kommentieren darf.");
        verify(replyAction).setEphemeral(true);
        verify(replyAction).queue();
    }

    @Test
    void setupRetryButtonReopensTheProviderConnectionModalWhenADraftExists() {
        setupDraftStore.start(USER_ID, GUILD_ID, CHANNEL_ID);
        when(providerConnectionService.activeConnection(GUILD_ID)).thenReturn(java.util.Optional.empty());
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        when(event.getUserLocale()).thenReturn(DiscordLocale.GERMAN);
        when(event.getGuildLocale()).thenReturn(DiscordLocale.GERMAN);
        when(event.getComponentId()).thenReturn(HolonetSetupRetryButton.BUTTON_ID);
        when(event.getUser()).thenReturn(user);
        ModalCallbackAction modalCallbackAction = mock(ModalCallbackAction.class, Answers.RETURNS_SELF);
        ArgumentCaptor<Modal> modalCaptor = ArgumentCaptor.forClass(Modal.class);
        when(event.replyModal(modalCaptor.capture())).thenReturn(modalCallbackAction);

        listener.onButtonInteraction(event);

        assertThat(modalCaptor.getValue().getId())
                .isEqualTo(de.medo.holonetbridge.commons.discord.modal.HolonetProviderConnectionModal.MODAL_ID);
        verify(modalCallbackAction).queue();
    }

    /**
     * {@code WebhookMessageCreateAction<T>} erases to a raw type via {@code .class}, so a single, documented
     * unchecked cast lives here instead of being repeated (and unsuppressed) at every call site.
     */
    @SuppressWarnings("unchecked")
    private static WebhookMessageCreateAction<Message> newSendMessageAction() {
        return mock(WebhookMessageCreateAction.class, Answers.RETURNS_SELF);
    }
}
