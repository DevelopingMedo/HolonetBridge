package de.medo.holonetbridge.commons.discord.button;

import de.holonetbridge.commons.message.SupportMessageCodes;
import de.holonetbridge.commons.message.DiscordMessageCodes;
import de.medo.holonetbridge.commons.discord.interaction.HolonetInteractionResponder;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

import java.util.function.Consumer;

import de.medo.holonetbridge.commons.discord.i18n.I18nProperties;
import de.medo.holonetbridge.commons.discord.i18n.MessageSourceConfiguration;
import de.medo.holonetbridge.commons.discord.listener.HolonetOnButtonInteractionListener;
import de.medo.holonetbridge.commons.discord.ticketing.*;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import java.util.Locale;
import java.util.List;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.SelfMember;

class HolonetCloseTicketButtonTest {
    private final HolonetChannelCoordinator channels = new HolonetChannelCoordinator();
    private final HolonetTicketArchiveService archive = mock(HolonetTicketArchiveService.class);
    private final org.springframework.context.MessageSource messages =
            new MessageSourceConfiguration().messageSource(new I18nProperties(null));
    private final HolonetCloseTicketButton button = new HolonetCloseTicketButton(
            messages, new HolonetInteractionResponder(messages), channels, archive);
    private final ButtonInteractionEvent event = mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);

    @Test
    void actualRestFailureCannotExposeExceptionMessageOrCauseInUserReply() {
        var hook = acknowledge();
        var guild = event.getGuild();
        var discord = event.getChannel().asTextChannel();
        var actor = mock(Member.class);
        when(guild.getIdLong()).thenReturn(1L);
        when(discord.getIdLong()).thenReturn(3L);
        when(discord.getGuild()).thenReturn(guild);
        when(actor.hasPermission(Permission.MANAGE_SERVER)).thenReturn(true);
        when(guild.retrieveMemberById(7L).useCache(false).complete()).thenReturn(actor);
        var bot = mock(SelfMember.class);
        when(guild.getSelfMember()).thenReturn(bot);
        when(bot.hasPermission(eq(discord), any(Permission[].class))).thenReturn(true);
        var rest = mock(HolonetDiscordChannelRest.class);
        when(rest.retrieve(discord)).thenThrow(new IllegalStateException("synthetic-sensitive-message",
                new RuntimeException("synthetic-sensitive-cause")));
        var realArchive = new HolonetTicketArchiveService(rest);

        new HolonetCloseTicketButton(messages, new HolonetInteractionResponder(messages), channels, realArchive).handle(event);

        var reply = ArgumentCaptor.forClass(String.class);
        verify(hook, timeout(3000)).editOriginal(reply.capture());
        assertThat(reply.getValue()).contains("ARCHIVE_DIAGNOSTIC phase=CHANNEL_GET",
                "exception=java.lang.IllegalStateException").doesNotContain("synthetic-sensitive", "I am missing the following permissions");
        verify(rest, never()).replace(any(), anyString(), anyList());
    }

    @Test
    void unregisteredChannelArchivesDiscordAndRepliesSuccess() {
        var hook = acknowledge();
        var guild = event.getGuild();
        var discord = event.getChannel().asTextChannel();
        var bot = mock(SelfMember.class);
        var actor = mock(Member.class);
        when(guild.getIdLong()).thenReturn(1L);
        when(discord.getIdLong()).thenReturn(3L);
        when(discord.getGuild()).thenReturn(guild);
        when(guild.getSelfMember()).thenReturn(bot);
        when(bot.getIdLong()).thenReturn(99L);
        when(bot.hasPermission(eq(discord), any(Permission[].class))).thenReturn(true);
        when(actor.hasPermission(Permission.MANAGE_SERVER)).thenReturn(true);
        when(guild.retrieveMemberById(7L).useCache(false).complete()).thenReturn(actor);
        var rest = mock(HolonetDiscordChannelRest.class);
        when(rest.retrieve(discord)).thenReturn(new HolonetDiscordChannelRest.Snapshot("support", List.of()));
        when(rest.replace(eq(discord), anyString(), anyList())).thenAnswer(call ->
                new HolonetDiscordChannelRest.Snapshot(call.getArgument(1), call.getArgument(2)));
        var realArchive = new HolonetTicketArchiveService(rest);

        new HolonetCloseTicketButton(messages, new HolonetInteractionResponder(messages), channels, realArchive).handle(event);

        var reply = ArgumentCaptor.forClass(String.class);
        verify(hook, timeout(3000)).editOriginal(reply.capture());
        assertThat(reply.getValue()).isEqualTo(messages.getMessage(SupportMessageCodes.CLOSE_DISCORD_SUCCESS, null, Locale.US));
        verify(rest).replace(eq(discord), anyString(), anyList());
    }

    @ParameterizedTest
    @EnumSource(HolonetArchiveDiagnostic.Phase.class)
    void diagnosticLineIsCopyableAndOnlyPreflightNamesMissingPermissions(HolonetArchiveDiagnostic.Phase phase) {
        var hook = acknowledge();
        var diagnostic = new HolonetArchiveDiagnostic(phase, "java.lang.IllegalStateException", null, null,
                phase == HolonetArchiveDiagnostic.Phase.BOT_PREFLIGHT
                        ? new HolonetArchiveDiagnostic.BotPermissions(false, true, true, false) : null,
                phase == HolonetArchiveDiagnostic.Phase.CONFIRMATION_MISMATCH ? false : null,
                phase == HolonetArchiveDiagnostic.Phase.CONFIRMATION_MISMATCH ? true : null);
        var key = SupportMessageCodes.CLOSE_DISCORD_FAILURE;
        when(archive.archive(any(), any(), eq(7L))).thenReturn(
                new HolonetTicketArchiveService.Result(key, diagnostic));

        button.handle(event);

        var reply = ArgumentCaptor.forClass(String.class);
        verify(hook, timeout(3000)).editOriginal(reply.capture());
        assertThat(reply.getValue()).contains(diagnostic.line(), messages.getMessage(key, null, Locale.US))
                .doesNotContain("synthetic-sensitive");
        if (phase == HolonetArchiveDiagnostic.Phase.BOT_PREFLIGHT) assertThat(reply.getValue()).contains(
                messages.getMessage(SupportMessageCodes.CLOSE_MISSING_PERMISSIONS, new Object[] {"View Channel"}, Locale.US));
        else assertThat(reply.getValue()).doesNotContain("I am missing the following permissions");
    }

    private InteractionHook acknowledge() {
        when(event.getUserLocale()).thenReturn(DiscordLocale.ENGLISH_US);
        when(event.getChannel().getType()).thenReturn(ChannelType.TEXT);
        when(event.getChannel().getIdLong()).thenReturn(3L);
        when(event.getUser().getIdLong()).thenReturn(7L);
        when(event.getMember().hasPermission(Permission.MANAGE_SERVER)).thenReturn(true);
        var hook = mock(InteractionHook.class, RETURNS_DEEP_STUBS);
        when(hook.getInteraction().getUserLocale()).thenReturn(DiscordLocale.ENGLISH_US);
        var reply = event.reply(messages.getMessage(SupportMessageCodes.CLOSE_IN_PROGRESS, null, Locale.US)).setEphemeral(true);
        doAnswer(call -> { ((Consumer<InteractionHook>) call.getArgument(0)).accept(hook); return null; })
                .when(reply).queue(any(), any());
        return hook;
    }

    @Test
    @SuppressWarnings("unchecked")
    void archiveStartsOnlyAfterSuccessfulProgressReply() {
        acknowledge();
        var reply = event.reply(messages.getMessage(SupportMessageCodes.CLOSE_IN_PROGRESS, null, Locale.US)).setEphemeral(true);
        doNothing().when(reply).queue(any(), any());

        button.handle(event);

        verifyNoInteractions(archive);
        var failure = ArgumentCaptor.forClass(Consumer.class);
        verify(reply).queue(any(), failure.capture());
        failure.getValue().accept(new IllegalStateException("Test reply failure"));
        verifyNoInteractions(archive);
    }

    @Test
    void asynchronousPermissionFailureUsesSharedResponderAndUserLocale() {
        var hook = acknowledge();
        var failure = mock(net.dv8tion.jda.api.exceptions.InsufficientPermissionException.class);
        when(failure.getPermission()).thenReturn(Permission.MANAGE_CHANNEL);
        when(archive.archive(any(), any(), eq(7L))).thenThrow(failure);

        button.handle(event);

        verify(hook, timeout(3000)).editOriginal(
                messages.getMessage(SupportMessageCodes.CLOSE_FAILURE, null, Locale.US) + "\n"
                        + messages.getMessage(DiscordMessageCodes.BOT_PERMISSION_MISSING,
                        new Object[]{Permission.MANAGE_CHANNEL.getName()}, Locale.US));
    }

    @Test
    void manageChannelAloneAndDirectMessagesCannotInvokeArchive() {
        when(event.getUserLocale()).thenReturn(DiscordLocale.ENGLISH_US);
        when(event.getMember().hasPermission(Permission.MANAGE_CHANNEL)).thenReturn(true);
        button.handle(event);
        verify(event).reply(messages.getMessage(DiscordMessageCodes.MANAGE_SERVER_REQUIRED, null, java.util.Locale.US));
        when(event.getGuild()).thenReturn(null);
        button.handle(event);
        verifyNoInteractions(archive);
        verify(event, never()).deferReply(anyBoolean());
    }

    @Test
    void correctButtonIsDispatchedAndEachClickRechecksPermissions() {
        when(event.isFromGuild()).thenReturn(true);
        when(event.getUserLocale()).thenReturn(DiscordLocale.ENGLISH_US);
        when(event.getComponentId()).thenReturn(HolonetCloseTicketButton.BUTTON_ID);
        when(event.getChannel().getType()).thenReturn(ChannelType.TEXT);
        when(event.getChannel().getIdLong()).thenReturn(3L);
        when(event.getUser().getIdLong()).thenReturn(7L);
        when(event.getMember().hasPermission(Permission.MANAGE_SERVER)).thenReturn(true);
        var hook = mock(InteractionHook.class, RETURNS_DEEP_STUBS);
        var reply = event.reply(messages.getMessage(SupportMessageCodes.CLOSE_IN_PROGRESS, null, Locale.US)).setEphemeral(true);
        doAnswer(call -> { ((Consumer<InteractionHook>) call.getArgument(0)).accept(hook); return null; })
                .when(reply).queue(any(), any());
        when(archive.archive(any(), any(), eq(7L)))
                .thenReturn(new HolonetTicketArchiveService.Result(SupportMessageCodes.CLOSE_DISCORD_SUCCESS,
                        null));
        var listener = new HolonetOnButtonInteractionListener(mock(HolonetOpenSupportButton.class),
                mock(HolonetReportBugButton.class), mock(HolonetReportFeatureButton.class), mock(HolonetAddWatcherButton.class), button);

        listener.onButtonInteraction(event);
        verify(hook, timeout(3000)).editOriginal(messages.getMessage(SupportMessageCodes.CLOSE_DISCORD_SUCCESS, null, Locale.US));
        when(event.getMember().hasPermission(Permission.MANAGE_SERVER)).thenReturn(false);
        listener.onButtonInteraction(event);
        verify(archive).archive(any(), any(), eq(7L));
        verify(event.getMember(), times(2)).hasPermission(Permission.MANAGE_SERVER);
    }

    @Test
    void nonTextChannelIsRejectedBeforeDeferral() {
        when(event.getUserLocale()).thenReturn(DiscordLocale.ENGLISH_US);
        when(event.getMember().hasPermission(Permission.MANAGE_SERVER)).thenReturn(true);
        when(event.getChannel().getType()).thenReturn(ChannelType.GUILD_PUBLIC_THREAD);
        button.handle(event);
        verifyNoInteractions(archive);
        verify(event, never()).deferReply(anyBoolean());
    }
}
