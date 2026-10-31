package de.medo.holonetbridge.commons.discord.ticketing;

import de.holonetbridge.commons.message.DiscordMessageCodes;
import de.holonetbridge.commons.message.SupportMessageCodes;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.SelfMember;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static de.medo.holonetbridge.commons.discord.ticketing.HolonetArchiveDiagnostic.Phase.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class HolonetTicketArchiveServiceTest {
    private final HolonetDiscordChannelRest rest = mock(HolonetDiscordChannelRest.class);
    private final HolonetTicketArchiveService archive = new HolonetTicketArchiveService(rest);
    private final Guild guild = mock(Guild.class, RETURNS_DEEP_STUBS);
    private final TextChannel channel = mock(TextChannel.class);
    private final Member actor = mock(Member.class);
    private final SelfMember bot = mock(SelfMember.class);

    @BeforeEach
    void setUp() {
        when(guild.getIdLong()).thenReturn(1L);
        when(channel.getGuild()).thenReturn(guild);
        when(guild.retrieveMemberById(7L).useCache(false).complete()).thenReturn(actor);
        when(actor.hasPermission(Permission.MANAGE_SERVER)).thenReturn(true);
        when(guild.getSelfMember()).thenReturn(bot);
        when(bot.getIdLong()).thenReturn(99L);
        when(bot.hasPermission(eq(channel), any(Permission[].class))).thenReturn(true);
        when(rest.retrieve(channel)).thenReturn(new HolonetDiscordChannelRest.Snapshot("support", List.of()));
        when(rest.replace(eq(channel), anyString(), anyList()))
                .thenAnswer(call -> new HolonetDiscordChannelRest.Snapshot(call.getArgument(1), call.getArgument(2)));
    }

    @Test
    void unregisteredChannelCanBeArchivedAndRepeatedWithoutAnotherPrefix() {
        assertThat(close().discordKey()).isEqualTo(SupportMessageCodes.CLOSE_DISCORD_SUCCESS);
        verify(rest).replace(eq(channel), eq("archived-support"), anyList());
        when(rest.retrieve(channel)).thenReturn(new HolonetDiscordChannelRest.Snapshot("archived-support", List.of()));
        assertThat(close().discordKey()).isEqualTo(SupportMessageCodes.CLOSE_DISCORD_SUCCESS);
        verify(rest, times(2)).replace(eq(channel), eq("archived-support"), anyList());
    }

    @Test
    void manageChannelAloneCannotArchive() {
        when(actor.hasPermission(Permission.MANAGE_SERVER)).thenReturn(false);
        when(actor.hasPermission(Permission.MANAGE_CHANNEL)).thenReturn(true);
        assertThat(close().discordKey()).isEqualTo(DiscordMessageCodes.MANAGE_SERVER_REQUIRED);
        verifyNoInteractions(rest);
    }

    @Test
    void differentGuildCannotBeArchived() {
        var otherGuild = mock(Guild.class);
        when(otherGuild.getIdLong()).thenReturn(5L);
        when(channel.getGuild()).thenReturn(otherGuild);
        assertThat(close().discordKey()).isEqualTo(SupportMessageCodes.ACTION_UNAVAILABLE);
        verifyNoInteractions(rest);
    }

    @ParameterizedTest
    @EnumSource(value = Permission.class, names = {"VIEW_CHANNEL", "MANAGE_CHANNEL", "MANAGE_PERMISSIONS"})
    void preflightReportsMissingEffectiveBotPermission(Permission missing) {
        when(bot.hasPermission(channel, missing)).thenReturn(false);
        var result = close();
        assertThat(result.discordKey()).isEqualTo(SupportMessageCodes.CLOSE_DISCORD_FAILURE);
        assertThat(result.diagnostic().phase()).isEqualTo(BOT_PREFLIGHT);
        assertThat(result.diagnostic().bot().missingMessageKeys()).containsExactly(switch (missing) {
            case VIEW_CHANNEL -> SupportMessageCodes.CLOSE_PERMISSION_VIEW;
            case MANAGE_CHANNEL -> SupportMessageCodes.CLOSE_PERMISSION_CHANNEL;
            default -> SupportMessageCodes.CLOSE_PERMISSION_PERMISSIONS;
        });
        verifyNoInteractions(rest);
    }

    @ParameterizedTest
    @EnumSource(value = HolonetArchiveDiagnostic.Phase.class, names = {"CHANNEL_GET", "CHANNEL_PATCH", "GET_PARSE", "PATCH_PARSE"})
    void restFailuresRetainOnlySafeDiagnostics(HolonetArchiveDiagnostic.Phase phase) {
        var cause = new IllegalStateException("synthetic-sensitive-message");
        var failure = HolonetArchiveDiagnostic.failure(phase, cause);
        if (phase == CHANNEL_GET || phase == GET_PARSE) when(rest.retrieve(channel)).thenThrow(failure);
        else when(rest.replace(eq(channel), anyString(), anyList())).thenThrow(failure);
        var result = close();
        assertThat(result.discordKey()).isEqualTo(SupportMessageCodes.CLOSE_DISCORD_FAILURE);
        assertThat(result.diagnostic().phase()).isEqualTo(phase);
        assertThat(result.diagnostic().bot()).isNull();
        assertThat(result.diagnostic().line()).contains("exception=java.lang.IllegalStateException")
                .doesNotContain("synthetic-sensitive");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void mismatchedNameOrProtectionCannotClaimSuccess(boolean nameMismatch) {
        when(rest.replace(eq(channel), anyString(), anyList())).thenAnswer(call ->
                new HolonetDiscordChannelRest.Snapshot(nameMismatch ? "other" : call.getArgument(1),
                        nameMismatch ? call.getArgument(2) : List.of()));
        var result = close();
        assertThat(result.discordKey()).isEqualTo(SupportMessageCodes.CLOSE_DISCORD_FAILURE);
        assertThat(result.diagnostic().phase()).isEqualTo(CONFIRMATION_MISMATCH);
        assertThat(result.diagnostic().nameMatches()).isEqualTo(!nameMismatch);
        assertThat(result.diagnostic().overridesMatch()).isEqualTo(nameMismatch);
    }

    @Test
    void archivePreservesManagementRightsAndDoesNotInventRequesterOverride() {
        long management = Permission.getRaw(Permission.MANAGE_CHANNEL, Permission.MANAGE_PERMISSIONS, Permission.MANAGE_THREADS);
        long read = Permission.getRaw(Permission.VIEW_CHANNEL, Permission.MESSAGE_HISTORY);
        when(rest.retrieve(channel)).thenReturn(new HolonetDiscordChannelRest.Snapshot("support", List.of(
                new HolonetDiscordChannelRest.Overwrite(8L, true, read | management | HolonetTicketArchiveService.WRITE_MASK, 0))));
        assertThat(close().discordKey()).isEqualTo(SupportMessageCodes.CLOSE_DISCORD_SUCCESS);
        verify(rest).replace(eq(channel), anyString(), argThat(overrides -> overrides.size() == 3 && overrides.contains(
                new HolonetDiscordChannelRest.Overwrite(8L, true, read | management, HolonetTicketArchiveService.WRITE_MASK))));
    }

    private HolonetTicketArchiveService.Result close() {
        return archive.archive(guild, channel, 7L);
    }
}
