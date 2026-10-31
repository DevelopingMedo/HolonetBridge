package de.medo.holonetbridge.commons.discord.ticketing;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.function.Supplier;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.Route;
import net.dv8tion.jda.api.requests.Request;
import net.dv8tion.jda.api.requests.Response;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;
import net.dv8tion.jda.internal.requests.RestActionImpl;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import org.springframework.stereotype.Component;
import static de.medo.holonetbridge.commons.discord.ticketing.HolonetArchiveDiagnostic.Phase.*;
import static de.medo.holonetbridge.commons.discord.ticketing.HolonetArchiveDiagnostic.at;

/** JDA 6.7 has no public channel/override refresh API. Keep the internal API dependency isolated here. */
@Component
public class HolonetDiscordChannelRest {
    public record Overwrite(long id, boolean member, long allow, long deny) {}
    public record Snapshot(String name, List<Overwrite> overrides) {
        public Snapshot { overrides = List.copyOf(overrides); }
    }
    protected record ChannelResponse(DataObject data, HolonetArchiveDiagnostic.Failure failure) {
        protected ChannelResponse(DataObject data) { this(data, null); }
        // JDA traces the parser's return value. Never let record rendering reveal the response data.
        @Override public String toString() { return "ArchiveChannelResponse"; }
        DataObject checkedData() {
            if (failure != null) throw failure;
            return data;
        }
    }

    public Snapshot retrieve(TextChannel channel) {
        var data = at(CHANNEL_GET, () -> request(channel, Route.Channels.GET_CHANNEL.compile(channel.getId()), null).complete());
        return at(GET_PARSE, () -> parse(channel, data.checkedData()));
    }

    public Snapshot replace(TextChannel channel, String name, List<Overwrite> overrides) {
        DataArray array = DataArray.empty();
        for (var override : overrides) array.add(DataObject.empty().put("id", Long.toUnsignedString(override.id()))
                .put("type", override.member() ? 1 : 0).put("allow", Long.toUnsignedString(override.allow()))
                .put("deny", Long.toUnsignedString(override.deny())));
        DataObject body = DataObject.empty().put("name", name).put("permission_overwrites", array);
        var data = at(CHANNEL_PATCH, () -> request(channel, Route.Channels.MODIFY_CHANNEL.compile(channel.getId()), body).complete());
        return at(PATCH_PARSE, () -> parse(channel, data.checkedData()));
    }

    protected RestAction<ChannelResponse> request(TextChannel channel, Route.CompiledRoute route, DataObject body) {
        return new ArchiveAction(channel.getJDA(), route, body);
    }

    private static ChannelResponse decodeResponse(Response response, HolonetArchiveDiagnostic.Phase phase, boolean httpError) {
        try {
            // Use JDAs decoded reader (including gzip/deflate), but give its TRACE logger only a safe
            // envelope. getObject/getString would instead hand that logger the full response content.
            return response.get(ChannelResponse.class, reader -> {
                try {
                    if (!httpError) return decode(phase, () -> DataObject.fromJson(reader));
                    var data = DataObject.fromJson(reader);
                    int code = data.getInt("code");
                    return new ChannelResponse(null, new HolonetArchiveDiagnostic.Failure(
                            new HolonetArchiveDiagnostic(phase, "NONE", ErrorResponse.fromCode(code), code, null, null, null)));
                } catch (Exception failure) {
                    return new ChannelResponse(null, HolonetArchiveDiagnostic.failure(phase, failure));
                }
            });
        } catch (RuntimeException failure) {
            return new ChannelResponse(null, HolonetArchiveDiagnostic.failure(phase, failure));
        }
    }

    private static final class ArchiveAction extends RestActionImpl<ChannelResponse> {
        private final HolonetArchiveDiagnostic.Phase requestPhase;

        ArchiveAction(JDA jda, Route.CompiledRoute route, DataObject body) {
            super(jda, route, body == null ? (RequestBody) null
                            : RequestBody.create(MediaType.parse("application/json"), body.toJson()),
                    (response, _) -> decodeResponse(response, body == null ? GET_PARSE : PATCH_PARSE, false));
            requestPhase = body == null ? CHANNEL_GET : CHANNEL_PATCH;
        }

        @Override
        public void handleResponse(Response response, Request<ChannelResponse> request) {
            if (response.isRateLimit()) {
                request.onRateLimited(response);
            } else if (response.isOk()) {
                super.handleResponse(response, request);
            } else {
                // JDA's default error factory can log malformed error bodies and their Throwable.
                // Extract only the code and complete with a safe failure envelope instead.
                ChannelResponse result;
                try {
                    if (response.isError()) {
                        var exception = response.getException();
                        result = new ChannelResponse(null, new HolonetArchiveDiagnostic.Failure(
                                new HolonetArchiveDiagnostic(requestPhase,
                                        exception == null ? "NONE" : exception.getClass().getName(),
                                        null, null, null, null, null)));
                    } else result = decodeResponse(response, requestPhase, true);
                } catch (RuntimeException failure) {
                    result = new ChannelResponse(null, HolonetArchiveDiagnostic.failure(requestPhase, failure));
                }
                request.onSuccess(result);
            }
        }
    }

    // Throwing from a JDA success handler can trigger internal Throwable logging. Return only a safe
    // failure envelope and raise it after complete(), outside that handler and its Requester catch.
    static ChannelResponse decode(HolonetArchiveDiagnostic.Phase phase, Supplier<DataObject> decoder) {
        try { return new ChannelResponse(decoder.get()); }
        catch (RuntimeException failure) {
            return new ChannelResponse(null, HolonetArchiveDiagnostic.failure(phase, failure));
        }
    }

    private Snapshot parse(TextChannel channel, DataObject data) {
        if (data.getLong("id") != channel.getIdLong() || data.getLong("guild_id") != channel.getGuild().getIdLong()
                || data.getInt("type") != 0) throw new IllegalStateException("Discord channel identity mismatch");
        List<Overwrite> overrides = new ArrayList<>();
        var seen = new HashSet<Long>();
        var array = data.getArray("permission_overwrites");
        for (int i = 0; i < array.length(); i++) {
            var override = array.getObject(i);
            long id = override.getLong("id");
            int type = override.getInt("type");
            long allow = override.getLong("allow"), deny = override.getLong("deny");
            if (id <= 0 || (type != 0 && type != 1) || (allow & deny) != 0 || !seen.add(id)) {
                throw new IllegalStateException("Invalid Discord permission snapshot");
            }
            overrides.add(new Overwrite(id, type == 1, allow, deny));
        }
        return new Snapshot(data.getString("name"), overrides);
    }
}
