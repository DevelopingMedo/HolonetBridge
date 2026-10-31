package de.medo.holonetbridge.commons.discord.ticketing;

import de.holonetbridge.commons.message.SupportMessageCodes;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.SelfMember;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.Route;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.junit.jupiter.api.Test;

class HolonetArchivePayloadTest {
    @Test
    void usesFreshRestSnapshotAndPreservesANewWatcherMissingFromTheGatewayCache() {
        var guild = mock(Guild.class, RETURNS_DEEP_STUBS);
        var channel = mock(TextChannel.class);
        var bot = mock(SelfMember.class);
        var actor = mock(Member.class);
        when(guild.getIdLong()).thenReturn(1L);
        when(guild.getSelfMember()).thenReturn(bot);
        when(guild.retrieveMemberById(7L).useCache(false).complete()).thenReturn(actor);
        when(actor.hasPermission(Permission.MANAGE_SERVER)).thenReturn(true);
        when(bot.getIdLong()).thenReturn(99L);
        when(bot.hasPermission(eq(channel), any(Permission[].class))).thenReturn(true);
        when(channel.getGuild()).thenReturn(guild);
        when(channel.getId()).thenReturn("3");
        when(channel.getIdLong()).thenReturn(3L);
        when(channel.getPermissionOverrides()).thenReturn(List.of());
        long read = Permission.getRaw(Permission.VIEW_CHANNEL, Permission.MESSAGE_HISTORY);
        long write = HolonetTicketArchiveService.WRITE_MASK;
        var original = DataObject.empty().put("id", "3").put("guild_id", "1").put("type", 0).put("name", "support");
        var array = DataArray.empty();
        for (long id : new long[] {2, 8, 9, 99}) array.add(DataObject.empty().put("id", Long.toString(id))
                .put("type", id == 9 ? 0 : 1).put("allow", Long.toString(read | write)).put("deny", "0"));
        original.put("permission_overwrites", array);
        class OfflineRest extends HolonetDiscordChannelRest {
            DataObject sent;
            int reads, patches;
            @Override protected RestAction<ChannelResponse> request(TextChannel ignored, Route.CompiledRoute route, DataObject body) {
                DataObject response;
                if (body == null) { reads++; response = original; }
                else {
                    assertThat(reads).isEqualTo(1);
                    patches++;
                    sent = DataObject.fromJson(body.toJson());
                    response = DataObject.fromJson(body.toJson()).put("id", "3").put("guild_id", "1").put("type", 0);
                }
                @SuppressWarnings("unchecked") RestAction<ChannelResponse> action = mock(RestAction.class);
                when(action.complete()).thenReturn(new ChannelResponse(response));
                return action;
            }
        }
        var rest = new OfflineRest();
        var service = new HolonetTicketArchiveService(rest);

        var result = service.archive(guild, channel, 7);

        assertThat(result.discordKey()).isEqualTo(SupportMessageCodes.CLOSE_DISCORD_SUCCESS);
        assertThat(rest.patches).isEqualTo(1);
        assertThat(rest.sent.keys()).containsExactlyInAnyOrder("name", "permission_overwrites");
        assertThat(rest.sent.getString("name")).isEqualTo("archived-support");
        var sent = rest.sent.getArray("permission_overwrites");
        Map<Long, DataObject> byId = new HashMap<>();
        for (int i = 0; i < sent.length(); i++) byId.put(sent.getObject(i).getLong("id"), sent.getObject(i));
        assertThat(byId).containsOnlyKeys(1L, 2L, 8L, 9L, 99L);
        for (long id : new long[] {1, 2, 8, 9}) {
            assertThat(byId.get(id).getLong("allow") & write).as("allow for %s", id).isZero();
            assertThat(byId.get(id).getLong("deny") & write).as("deny for %s", id).isEqualTo(write);
        }
        for (long id : new long[] {2, 8, 9}) assertThat(byId.get(id).getLong("allow") & read).isEqualTo(read);
        assertThat(byId.get(99L).getLong("allow") & Permission.MESSAGE_SEND.getRawValue()).isNotZero();
        assertThat(byId.get(2L).getLong("deny") & Permission.MANAGE_PERMISSIONS.getRawValue()).isZero();
        verify(channel, never()).getPermissionOverrides();
        verify(channel, never()).getManager();
    }

    @Test
    void mismatchedFreshChannelIdentityIsRejectedBeforePatch() {
        var channel = mock(TextChannel.class, RETURNS_DEEP_STUBS);
        when(channel.getId()).thenReturn("3");
        when(channel.getIdLong()).thenReturn(3L);
        when(channel.getGuild().getIdLong()).thenReturn(1L);
        var rest = new HolonetDiscordChannelRest() {
            @Override protected RestAction<ChannelResponse> request(TextChannel ignored, Route.CompiledRoute route, DataObject body) {
                @SuppressWarnings("unchecked") RestAction<ChannelResponse> action = mock(RestAction.class);
                when(action.complete()).thenReturn(new ChannelResponse(DataObject.empty().put("id", "4").put("guild_id", "1").put("type", 0)));
                return action;
            }
        };
        assertThatThrownBy(() -> rest.retrieve(channel)).isInstanceOf(HolonetArchiveDiagnostic.Failure.class)
                .satisfies(failure -> assertThat(((HolonetArchiveDiagnostic.Failure) failure).diagnostic().phase())
                        .isEqualTo(HolonetArchiveDiagnostic.Phase.GET_PARSE));
    }
}
