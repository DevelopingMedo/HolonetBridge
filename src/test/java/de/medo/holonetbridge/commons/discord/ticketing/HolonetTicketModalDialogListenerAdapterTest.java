package de.medo.holonetbridge.commons.discord.ticketing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.medo.holonetbridge.commons.discord.DiscordGatewayConfiguration;
import de.medo.holonetbridge.commons.ticket.TicketDto;
import de.medo.holonetbridge.commons.ticket.TicketProvider;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.modals.Modal;
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

    private final HolonetTicketCreationSessionStore sessionStore = new HolonetTicketCreationSessionStore();
    private final TicketProvider ticketProvider = mock(TicketProvider.class);
    private final HolonetTicketModalDialogListenerAdapter listener =
            new HolonetTicketModalDialogListenerAdapter(sessionStore, ticketProvider);

    private final User user = mock(User.class);

    @BeforeEach
    void stubUserId() {
        when(user.getIdLong()).thenReturn(USER_ID);
    }

    @Test
    void slashCommandStartsASessionAndOffersTheThreeChoices() {
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        when(event.getName()).thenReturn(DiscordGatewayConfiguration.COMMAND_TICKET);
        when(event.getUser()).thenReturn(user);
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class, Answers.RETURNS_SELF);
        when(event.reply(anyString())).thenReturn(replyAction);

        listener.onSlashCommandInteraction(event);

        assertThat(sessionStore.get(USER_ID)).isPresent();
        verify(event).reply("Möchtest du ein Ticket anlegen?");
        verify(replyAction).setEphemeral(true);
        verify(replyAction).queue();
    }

    @Test
    void persistentSupportButtonOpensTheSameChooserAsTheSlashCommand() {
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        when(event.getComponentId()).thenReturn(HolonetTicketModalDialogListenerAdapter.BUTTON_OPEN);
        when(event.getUser()).thenReturn(user);
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class, Answers.RETURNS_SELF);
        when(event.reply(anyString())).thenReturn(replyAction);

        listener.onButtonInteraction(event);

        assertThat(sessionStore.get(USER_ID)).isPresent();
        verify(event).reply("Möchtest du ein Ticket anlegen?");
        verify(replyAction).setEphemeral(true);
        verify(replyAction).queue();
    }

    @Test
    void slashCommandIgnoresOtherCommandNames() {
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        when(event.getName()).thenReturn("not-ticket");

        listener.onSlashCommandInteraction(event);

        assertThat(sessionStore.get(USER_ID)).isEmpty();
        verify(event, never()).reply(anyString());
    }

    @Test
    void startCreateButtonOpensTheTitleModal() {
        sessionStore.startOrReset(USER_ID);
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        when(event.getComponentId()).thenReturn("ticket:start:create");
        when(event.getUser()).thenReturn(user);
        ModalCallbackAction modalCallbackAction = mock(ModalCallbackAction.class, Answers.RETURNS_SELF);
        ArgumentCaptor<Modal> modalCaptor = ArgumentCaptor.forClass(Modal.class);
        when(event.replyModal(modalCaptor.capture())).thenReturn(modalCallbackAction);

        listener.onButtonInteraction(event);

        assertThat(modalCaptor.getValue().getId()).isEqualTo("ticket:modal:title");
        verify(modalCallbackAction).queue();
    }

    @Test
    void startCancelButtonClearsTheSessionAndConfirms() {
        sessionStore.startOrReset(USER_ID);
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        when(event.getComponentId()).thenReturn("ticket:start:cancel");
        when(event.getUser()).thenReturn(user);
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class, Answers.RETURNS_SELF);
        when(event.reply(anyString())).thenReturn(replyAction);

        listener.onButtonInteraction(event);

        assertThat(sessionStore.get(USER_ID)).isEmpty();
        verify(event).reply("Abgebrochen.");
        verify(replyAction).setEphemeral(true);
    }

    @Test
    void startChangeButtonRepliesNotYetImplemented() {
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        when(event.getComponentId()).thenReturn("ticket:start:change");
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class, Answers.RETURNS_SELF);
        when(event.reply(anyString())).thenReturn(replyAction);

        listener.onButtonInteraction(event);

        verify(event).reply("Das Ändern bestehender Tickets ist noch nicht implementiert.");
        verify(replyAction).setEphemeral(true);
    }

    @Test
    void titleModalSubmissionStoresTitleAndAsksForTheKind() {
        sessionStore.startOrReset(USER_ID);
        ModalInteractionEvent event = mock(ModalInteractionEvent.class);
        when(event.getModalId()).thenReturn("ticket:modal:title");
        when(event.getUser()).thenReturn(user);
        ModalMapping titleMapping = mock(ModalMapping.class);
        when(titleMapping.getAsString()).thenReturn("Login fails after update");
        when(event.getValue("title")).thenReturn(titleMapping);
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class, Answers.RETURNS_SELF);
        when(event.reply(anyString())).thenReturn(replyAction);

        listener.onModalInteraction(event);

        assertThat(sessionStore.get(USER_ID)).hasValueSatisfying(
                session -> assertThat(session.title()).isEqualTo("Login fails after update"));
        verify(event).reply("Ist es ein Bug oder ein Feature?");
        verify(replyAction).setEphemeral(true);
    }

    @Test
    void titleModalSubmissionWithoutASessionAsksToRestart() {
        ModalInteractionEvent event = mock(ModalInteractionEvent.class);
        when(event.getModalId()).thenReturn("ticket:modal:title");
        when(event.getUser()).thenReturn(user);
        ModalMapping titleMapping = mock(ModalMapping.class);
        when(titleMapping.getAsString()).thenReturn("Some title");
        when(event.getValue("title")).thenReturn(titleMapping);
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class, Answers.RETURNS_SELF);
        when(event.reply(anyString())).thenReturn(replyAction);

        listener.onModalInteraction(event);

        verify(event).reply("Die Sitzung ist abgelaufen, bitte starte mit /ticket neu.");
    }

    @Test
    void bugButtonStoresKindAndOpensDescriptionModalPrefilledWithTheTemplate() {
        sessionStore.startOrReset(USER_ID).title("Login fails");
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        when(event.getComponentId()).thenReturn("ticket:type:bug");
        when(event.getUser()).thenReturn(user);
        ModalCallbackAction modalCallbackAction = mock(ModalCallbackAction.class, Answers.RETURNS_SELF);
        ArgumentCaptor<Modal> modalCaptor = ArgumentCaptor.forClass(Modal.class);
        when(event.replyModal(modalCaptor.capture())).thenReturn(modalCallbackAction);

        listener.onButtonInteraction(event);

        assertThat(sessionStore.get(USER_ID)).hasValueSatisfying(
                session -> assertThat(session.kind()).isEqualTo(HolonetTicketKind.BUG));
        assertThat(modalCaptor.getValue().getId()).isEqualTo("ticket:modal:description");
    }

    @Test
    void descriptionModalSubmissionForABugCreatesATicketWithTheBugPrefixAndConfirms() {
        HolonetTicketCreationSession session = sessionStore.startOrReset(USER_ID);
        session.title("Login fails");
        session.kind(HolonetTicketKind.BUG);

        ModalInteractionEvent event = mock(ModalInteractionEvent.class);
        when(event.getModalId()).thenReturn("ticket:modal:description");
        when(event.getUser()).thenReturn(user);
        ModalMapping descriptionMapping = mock(ModalMapping.class);
        when(descriptionMapping.getAsString()).thenReturn("Steps to reproduce...");
        when(event.getValue("description")).thenReturn(descriptionMapping);
        ReplyCallbackAction deferAction = mock(ReplyCallbackAction.class, Answers.RETURNS_SELF);
        when(event.deferReply(true)).thenReturn(deferAction);
        InteractionHook hook = mock(InteractionHook.class);
        when(event.getHook()).thenReturn(hook);
        WebhookMessageCreateAction<Message> sendAction = newSendMessageAction();
        when(hook.sendMessage(anyString())).thenReturn(sendAction);

        when(ticketProvider.create("[Bug] Login fails", "Steps to reproduce..."))
                .thenReturn(new TicketDto("42", "[Bug] Login fails", "Steps to reproduce..."));

        listener.onModalInteraction(event);

        verify(ticketProvider).create("[Bug] Login fails", "Steps to reproduce...");
        verify(hook).sendMessage("Ticket #42 wurde angelegt: [Bug] Login fails");
        verify(sendAction).queue();
        assertThat(sessionStore.get(USER_ID)).isEmpty();
    }

    @Test
    void descriptionModalSubmissionForAFeatureCreatesATicketWithTheFeaturePrefix() {
        HolonetTicketCreationSession session = sessionStore.startOrReset(USER_ID);
        session.title("Dark mode");
        session.kind(HolonetTicketKind.FEATURE);

        ModalInteractionEvent event = mock(ModalInteractionEvent.class);
        when(event.getModalId()).thenReturn("ticket:modal:description");
        when(event.getUser()).thenReturn(user);
        ModalMapping descriptionMapping = mock(ModalMapping.class);
        when(descriptionMapping.getAsString()).thenReturn("I want a dark theme toggle in settings.");
        when(event.getValue("description")).thenReturn(descriptionMapping);
        when(event.deferReply(true)).thenReturn(mock(ReplyCallbackAction.class, Answers.RETURNS_SELF));
        InteractionHook hook = mock(InteractionHook.class);
        when(event.getHook()).thenReturn(hook);
        when(hook.sendMessage(anyString())).thenReturn(newSendMessageAction());

        when(ticketProvider.create(eq("[Feature] Dark mode"), anyString()))
                .thenReturn(new TicketDto("7", "[Feature] Dark mode", "I want a dark theme toggle in settings."));

        listener.onModalInteraction(event);

        verify(ticketProvider).create("[Feature] Dark mode", "I want a dark theme toggle in settings.");
        verify(hook).sendMessage(anyString());
    }

    /**
     * {@code WebhookMessageCreateAction<T>} erases to a raw type via {@code .class}, so a single, documented
     * unchecked cast lives here instead of being repeated (and unsuppressed) at every call site.
     */
    @SuppressWarnings("unchecked")
    private static WebhookMessageCreateAction<Message> newSendMessageAction() {
        return mock(WebhookMessageCreateAction.class, Answers.RETURNS_SELF);
    }

    @Test
    void descriptionModalSubmissionWithoutASessionAsksToRestart() {
        ModalInteractionEvent event = mock(ModalInteractionEvent.class);
        when(event.getModalId()).thenReturn("ticket:modal:description");
        when(event.getUser()).thenReturn(user);
        ModalMapping descriptionMapping = mock(ModalMapping.class);
        when(descriptionMapping.getAsString()).thenReturn("Some description");
        when(event.getValue("description")).thenReturn(descriptionMapping);
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class, Answers.RETURNS_SELF);
        when(event.reply(anyString())).thenReturn(replyAction);

        listener.onModalInteraction(event);

        verify(event).reply("Die Sitzung ist abgelaufen, bitte starte mit /ticket neu.");
        verify(ticketProvider, never()).create(anyString(), anyString());
    }

    @Test
    void unknownButtonIdsAreIgnored() {
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        when(event.getComponentId()).thenReturn("something:else");

        listener.onButtonInteraction(event);

        verify(event, never()).reply(anyString());
        verify(event, never()).replyModal(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unknownModalIdsAreIgnored() {
        ModalInteractionEvent event = mock(ModalInteractionEvent.class);
        when(event.getModalId()).thenReturn("something:else");

        listener.onModalInteraction(event);

        verify(event, never()).reply(anyString());
        verify(event, never()).deferReply(anyBoolean());
    }
}
