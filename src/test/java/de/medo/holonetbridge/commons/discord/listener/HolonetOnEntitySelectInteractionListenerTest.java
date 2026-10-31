package de.medo.holonetbridge.commons.discord.listener;

import de.holonetbridge.commons.message.SupportMessageCodes;
import de.medo.holonetbridge.commons.discord.interaction.HolonetInteractionResponder;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.function.Consumer;

import de.medo.holonetbridge.commons.discord.i18n.I18nProperties;
import de.medo.holonetbridge.commons.discord.i18n.MessageSourceConfiguration;
import de.medo.holonetbridge.commons.discord.select.HolonetGrantWatcherAction;
import de.medo.holonetbridge.commons.discord.ticketing.HolonetChannelCoordinator;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.junit.jupiter.api.Test;

class HolonetOnEntitySelectInteractionListenerTest {
    @Test
    void nonGuildSelectionsAreIgnoredBeforeDispatchOrReply() {
        var action = mock(HolonetGrantWatcherAction.class);
        var event = mock(EntitySelectInteractionEvent.class);

        new HolonetOnEntitySelectInteractionListener(action).onEntitySelectInteraction(event);

        verify(event).isFromGuild();
        verifyNoMoreInteractions(event);
        verifyNoInteractions(action);
    }

    @Test
    void watcherSelectionGrantsViewAndCommentPermissionsAfterAcknowledging() {
        var messages = new MessageSourceConfiguration().messageSource(new I18nProperties(null));
        var channels = new HolonetChannelCoordinator();
        var listener = new HolonetOnEntitySelectInteractionListener(
                new HolonetGrantWatcherAction(messages, new HolonetInteractionResponder(messages), channels));
        var event = mock(EntitySelectInteractionEvent.class, RETURNS_DEEP_STUBS);
        when(event.isFromGuild()).thenReturn(true);
        var member = mock(Member.class, RETURNS_DEEP_STUBS);
        var hook = mock(InteractionHook.class, RETURNS_DEEP_STUBS);
        when(event.getGuild().getIdLong()).thenReturn(1L);
        when(event.getUser().getIdLong()).thenReturn(2L);
        when(event.getChannel().getIdLong()).thenReturn(3L);
        when(member.getGuild().getIdLong()).thenReturn(1L);
        when(member.getAsMention()).thenReturn("<@888>");
        when(event.getUserLocale()).thenReturn(DiscordLocale.GERMAN);
        when(event.getComponentId()).thenReturn("ticket:watcher:select");
        when(event.getMentions().getMembers()).thenReturn(List.of(member));
        var reply = event.reply(messages.getMessage(SupportMessageCodes.WATCHER_ADD_IN_PROGRESS, null, java.util.Locale.GERMAN)).setEphemeral(true);
        doAnswer(call -> { ((Consumer<InteractionHook>) call.getArgument(0)).accept(hook); return null; })
                .when(reply).queue(any(), any());

        listener.onEntitySelectInteraction(event);

        verify(hook, timeout(3000)).editOriginal("<@888> wurde als Beobachter hinzugefügt.");
        verify(event.getChannel().asTextChannel().upsertPermissionOverride(member)).grant(
                Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY, Permission.MESSAGE_ATTACH_FILES);
        verify(event.getChannel().asTextChannel().upsertPermissionOverride(member)
                .grant(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY,
                        Permission.MESSAGE_ATTACH_FILES)).complete();
    }
}
