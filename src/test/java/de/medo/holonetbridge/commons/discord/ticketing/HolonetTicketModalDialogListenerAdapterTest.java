package de.medo.holonetbridge.commons.discord.ticketing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.medo.holonetbridge.commons.ticket.TicketDto;
import de.medo.holonetbridge.commons.ticket.TicketProvider;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Mentions;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.SelfMember;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.requests.restaction.ChannelAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.PermissionOverrideAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ModalCallbackAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Answers;

/**
 * Drives {@link HolonetTicketModalDialogListenerAdapter} through its button/modal interactions using Mockito
 * mocks of the JDA event types directly, without any real gateway connection.
 *
 * <p>Fluent JDA actions ({@link ReplyCallbackAction}, {@link ModalCallbackAction}, {@link
 * WebhookMessageCreateAction}) are interfaces whose chained setter methods are themselves mocked unless stubbed,
 * so they are mocked with {@link Answers#RETURNS_SELF} to make the {@code .setEphemeral(true).queue()} style
 * chains used in production code work without stubbing every single step.
 */
class HolonetTicketModalDialogListenerAdapterTest {

    private static final long USER_ID = 123456789L;
    private static final long GUILD_ID = 999L;
    private static final long CHANNEL_ID = 111L;

    private final HolonetTicketCreationSessionStore sessionStore = new HolonetTicketCreationSessionStore();
    private final TicketProvider ticketProvider = mock(TicketProvider.class);
    private final HolonetSupportChannelRepository supportChannelRepository =
            mock(HolonetSupportChannelRepository.class);
    private final HolonetTicketModalDialogListenerAdapter listener =
            new HolonetTicketModalDialogListenerAdapter(sessionStore, ticketProvider, supportChannelRepository);

    private final User user = mock(User.class);

    @BeforeEach
    void stubUserId() {
        when(user.getIdLong()).thenReturn(USER_ID);
    }

    @Test
    void supportButtonCreatesAPrivateChannelAndPostsTheKindChooser() {
        Guild guild = mock(Guild.class);
        when(guild.getIdLong()).thenReturn(GUILD_ID);
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
        when(event.getComponentId()).thenReturn(HolonetTicketModalDialogListenerAdapter.BUTTON_OPEN);
        when(event.getGuild()).thenReturn(guild);
        when(event.getMember()).thenReturn(member);
        when(event.getChannel()).thenReturn(buttonChannelUnion);
        when(event.deferReply(true)).thenReturn(mock(ReplyCallbackAction.class, Answers.RETURNS_SELF));
        InteractionHook hook = mock(InteractionHook.class);
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

        assertThat(sessionStore.get(USER_ID)).isPresent();
        verify(createdChannel).sendMessage("Hast du ein Bug gefunden oder hast du eine neue Idee?");
        verify(hook).sendMessage("Support-Kanal erstellt: #holonetbridge-00001");
    }

    @Test
    void bugButtonStoresKindAndOpensTheBugReportModal() {
        sessionStore.startOrReset(USER_ID);
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        when(event.getComponentId()).thenReturn("ticket:kind:bug");
        when(event.getUser()).thenReturn(user);
        ModalCallbackAction modalCallbackAction = mock(ModalCallbackAction.class, Answers.RETURNS_SELF);
        ArgumentCaptor<Modal> modalCaptor = ArgumentCaptor.forClass(Modal.class);
        when(event.replyModal(modalCaptor.capture())).thenReturn(modalCallbackAction);

        listener.onButtonInteraction(event);

        assertThat(sessionStore.get(USER_ID)).hasValueSatisfying(
                session -> assertThat(session.kind()).isEqualTo(HolonetTicketKind.BUG));
        assertThat(modalCaptor.getValue().getId()).isEqualTo("ticket:modal:bug-report");
        verify(modalCallbackAction).queue();
    }

    @Test
    void featureButtonStoresKindAndOpensTheFeatureModal() {
        sessionStore.startOrReset(USER_ID);
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        when(event.getComponentId()).thenReturn("ticket:kind:feature");
        when(event.getUser()).thenReturn(user);
        ModalCallbackAction modalCallbackAction = mock(ModalCallbackAction.class, Answers.RETURNS_SELF);
        ArgumentCaptor<Modal> modalCaptor = ArgumentCaptor.forClass(Modal.class);
        when(event.replyModal(modalCaptor.capture())).thenReturn(modalCallbackAction);

        listener.onButtonInteraction(event);

        assertThat(sessionStore.get(USER_ID)).hasValueSatisfying(
                session -> assertThat(session.kind()).isEqualTo(HolonetTicketKind.FEATURE));
        assertThat(modalCaptor.getValue().getId()).isEqualTo("ticket:modal:feature");
        verify(modalCallbackAction).queue();
    }

    @Test
    void bugReportModalSubmissionCreatesABugTicketAndPostsTheConfirmationInTheChannel() {
        sessionStore.startOrReset(USER_ID).kind(HolonetTicketKind.BUG);

        ModalInteractionEvent event = mock(ModalInteractionEvent.class);
        when(event.getModalId()).thenReturn("ticket:modal:bug-report");
        when(event.getUser()).thenReturn(user);
        ModalMapping title = textMapping("Login fails");
        ModalMapping steps = textMapping("Did X then Y");
        ModalMapping expected = textMapping("It should work");
        ModalMapping actual = textMapping("It crashed");
        ModalMapping recurrence = selectionMapping("Jedes Mal");
        when(event.getValue("title")).thenReturn(title);
        when(event.getValue("steps")).thenReturn(steps);
        when(event.getValue("expected")).thenReturn(expected);
        when(event.getValue("actual")).thenReturn(actual);
        when(event.getValue("recurrence")).thenReturn(recurrence);
        when(event.deferReply(true)).thenReturn(mock(ReplyCallbackAction.class, Answers.RETURNS_SELF));
        TextChannel channel = stubModalChannel(event);
        when(channel.sendMessage(anyString())).thenReturn(mock(MessageCreateAction.class, Answers.RETURNS_SELF));
        when(ticketProvider.create(eq("[Bug] Login fails"), anyString()))
                .thenReturn(new TicketDto("42", "[Bug] Login fails", "ignored"));

        listener.onModalInteraction(event);

        ArgumentCaptor<String> descriptionCaptor = ArgumentCaptor.forClass(String.class);
        verify(ticketProvider).create(eq("[Bug] Login fails"), descriptionCaptor.capture());
        assertThat(descriptionCaptor.getValue())
                .contains("**Was hast du vor dem Fehler gemacht?**", "Did X then Y")
                .contains("**Was hättest du erwartet?**", "It should work")
                .contains("**Was ist stattdessen passiert?**", "It crashed")
                .contains("**Tritt der Fehler erneut auf?**", "Jedes Mal");
        verify(channel).sendMessage("Ticket #42 wurde angelegt: [Bug] Login fails");
        assertThat(sessionStore.get(USER_ID)).isEmpty();
    }

    @Test
    void featureModalSubmissionCreatesAFeatureTicketAndPostsTheConfirmationInTheChannel() {
        sessionStore.startOrReset(USER_ID).kind(HolonetTicketKind.FEATURE);

        ModalInteractionEvent event = mock(ModalInteractionEvent.class);
        when(event.getModalId()).thenReturn("ticket:modal:feature");
        when(event.getUser()).thenReturn(user);
        ModalMapping title = textMapping("Dark mode");
        ModalMapping description = textMapping("I want a dark theme toggle in settings.");
        when(event.getValue("title")).thenReturn(title);
        when(event.getValue("description")).thenReturn(description);
        when(event.deferReply(true)).thenReturn(mock(ReplyCallbackAction.class, Answers.RETURNS_SELF));
        TextChannel channel = stubModalChannel(event);
        when(channel.sendMessage(anyString())).thenReturn(mock(MessageCreateAction.class, Answers.RETURNS_SELF));
        when(ticketProvider.create(eq("[Feature] Dark mode"), anyString()))
                .thenReturn(new TicketDto("7", "[Feature] Dark mode", "I want a dark theme toggle in settings."));

        listener.onModalInteraction(event);

        verify(ticketProvider).create("[Feature] Dark mode", "I want a dark theme toggle in settings.");
        verify(channel).sendMessage("Ticket #7 wurde angelegt: [Feature] Dark mode");
        assertThat(sessionStore.get(USER_ID)).isEmpty();
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
    void unknownButtonIdsAreIgnored() {
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        when(event.getComponentId()).thenReturn("something:else");

        listener.onButtonInteraction(event);

        verify(event, never()).reply(anyString());
        verify(event, never()).replyModal(any());
    }

    @Test
    void unknownModalIdsAreIgnored() {
        ModalInteractionEvent event = mock(ModalInteractionEvent.class);
        when(event.getModalId()).thenReturn("something:else");

        listener.onModalInteraction(event);

        verify(event, never()).reply(anyString());
        verify(event, never()).deferReply(anyBoolean());
    }

    @Test
    void watcherButtonOpensTheUserSelector() {
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        when(event.getComponentId()).thenReturn("ticket:watcher:add");
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class, Answers.RETURNS_SELF);
        when(event.reply(anyString())).thenReturn(replyAction);

        listener.onButtonInteraction(event);

        verify(event).reply("Wähle eine Person aus, die den Kanal sehen und kommentieren darf.");
        verify(replyAction).setEphemeral(true);
        verify(replyAction).queue();
    }

    @Test
    void watcherSelectionGrantsViewAndCommentPermissions() {
        Member member = mock(Member.class);
        when(member.getAsMention()).thenReturn("<@888>");

        Mentions mentions = mock(Mentions.class);
        when(mentions.getMembers()).thenReturn(List.of(member));

        MessageChannelUnion union = mock(MessageChannelUnion.class);
        TextChannel channel = mock(TextChannel.class);
        when(union.asTextChannel()).thenReturn(channel);
        PermissionOverrideAction overrideAction = mock(PermissionOverrideAction.class, Answers.RETURNS_SELF);
        when(channel.upsertPermissionOverride(member)).thenReturn(overrideAction);

        EntitySelectInteractionEvent event = mock(EntitySelectInteractionEvent.class);
        when(event.getComponentId()).thenReturn("ticket:watcher:select");
        when(event.getMentions()).thenReturn(mentions);
        when(event.getChannel()).thenReturn(union);
        when(event.deferReply(true)).thenReturn(mock(ReplyCallbackAction.class, Answers.RETURNS_SELF));
        InteractionHook hook = mock(InteractionHook.class);
        when(event.getHook()).thenReturn(hook);
        when(hook.sendMessage(anyString())).thenReturn(newSendMessageAction());

        listener.onEntitySelectInteraction(event);

        verify(channel).upsertPermissionOverride(member);
        verify(overrideAction).grant(
                Permission.VIEW_CHANNEL,
                Permission.MESSAGE_SEND,
                Permission.MESSAGE_HISTORY,
                Permission.MESSAGE_ATTACH_FILES);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<PermissionOverride>> successCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(overrideAction).queue(successCaptor.capture(), any());
        successCaptor.getValue().accept(null);

        verify(hook).sendMessage("<@888> wurde als Beobachter hinzugefügt.");
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

    /**
     * {@code WebhookMessageCreateAction<T>} erases to a raw type via {@code .class}, so a single, documented
     * unchecked cast lives here instead of being repeated (and unsuppressed) at every call site.
     */
    @SuppressWarnings("unchecked")
    private static WebhookMessageCreateAction<Message> newSendMessageAction() {
        return mock(WebhookMessageCreateAction.class, Answers.RETURNS_SELF);
    }
}
