package de.medo.holonetbridge.commons.discord.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.medo.holonetbridge.commons.discord.select.HolonetGrantWatcherAction;
import java.util.List;
import java.util.function.Consumer;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Mentions;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.PermissionOverrideAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Answers;

/**
 * Drives {@link HolonetOnEntitySelectInteractionListener} through its watcher selection using Mockito mocks of the
 * JDA event types directly, without any real gateway connection.
 */
class HolonetOnEntitySelectInteractionListenerTest {

    private final HolonetOnEntitySelectInteractionListener listener =
            new HolonetOnEntitySelectInteractionListener(new HolonetGrantWatcherAction());

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

    /**
     * {@code WebhookMessageCreateAction<T>} erases to a raw type via {@code .class}, so a single, documented
     * unchecked cast lives here instead of being repeated (and unsuppressed) at every call site.
     */
    @SuppressWarnings("unchecked")
    private static WebhookMessageCreateAction<Message> newSendMessageAction() {
        return mock(WebhookMessageCreateAction.class, Answers.RETURNS_SELF);
    }
}
