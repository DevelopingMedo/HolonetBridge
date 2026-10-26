package de.medo.holonetbridge.commons.discord.ticketing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.medo.holonetbridge.commons.ticket.TicketDto;
import de.medo.holonetbridge.commons.ticket.TicketProvider;
import java.util.List;
import java.util.function.Consumer;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Mentions;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.PermissionOverrideAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Answers;

/**
 * Drives {@link HolonetTicketModalDialogListenerAdapter} through its modal and watcher-selection interactions using
 * Mockito mocks of the JDA event types directly, without any real gateway connection. The button interactions that
 * precede these steps are covered by {@code HolonetOnButtonInteractionListenerTest}.
 *
 * <p>Fluent JDA actions ({@link ReplyCallbackAction}, {@link WebhookMessageCreateAction}) are interfaces whose
 * chained setter methods are themselves mocked unless stubbed, so they are mocked with {@link Answers#RETURNS_SELF}
 * to make the {@code .setEphemeral(true).queue()} style chains used in production code work without stubbing every
 * single step.
 */
class HolonetTicketModalDialogListenerAdapterTest {

    private static final long USER_ID = 123456789L;
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
    void unknownModalIdsAreIgnored() {
        ModalInteractionEvent event = mock(ModalInteractionEvent.class);
        when(event.getModalId()).thenReturn("something:else");

        listener.onModalInteraction(event);

        verify(event, never()).reply(anyString());
        verify(event, never()).deferReply(anyBoolean());
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
