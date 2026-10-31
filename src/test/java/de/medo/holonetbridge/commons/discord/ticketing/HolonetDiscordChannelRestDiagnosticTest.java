package de.medo.holonetbridge.commons.discord.ticketing;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static de.medo.holonetbridge.commons.discord.ticketing.HolonetArchiveDiagnostic.Phase.*;

import java.util.List;
import java.util.stream.Stream;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.Route;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.EnumSource;

class HolonetDiscordChannelRestDiagnosticTest {
    private final TextChannel channel = mock(TextChannel.class, RETURNS_DEEP_STUBS);

    static Stream<Arguments> invalidFields() {
        return Stream.of(GET_PARSE, PATCH_PARSE).flatMap(phase -> Stream.of(
                "{\"guild_id\":\"1\",\"type\":0,\"name\":\"synthetic-sensitive-name\",\"permission_overwrites\":[]}",
                "{\"id\":\"4\",\"guild_id\":\"1\",\"type\":0}",
                "{\"id\":\"3\",\"guild_id\":\"9\",\"type\":0}",
                "{\"id\":\"3\",\"guild_id\":\"1\",\"type\":11}",
                "{\"id\":\"3\",\"guild_id\":\"1\",\"type\":0,\"permission_overwrites\":\"synthetic-sensitive-field\"}",
                body("[{\"id\":\"8\",\"type\":7,\"allow\":\"0\",\"deny\":\"0\"}]"),
                body("[{\"id\":\"8\",\"type\":1,\"allow\":\"1\",\"deny\":\"1\"}]"),
                body("[{\"id\":\"8\",\"type\":1,\"allow\":\"synthetic-sensitive-mask\",\"deny\":\"0\"}]"),
                body("[{\"id\":\"8\",\"type\":1,\"allow\":\"0\",\"deny\":\"0\"}, {\"id\":\"8\",\"type\":1,\"allow\":\"0\",\"deny\":\"0\"}]"),
                "{\"id\":\"3\",\"guild_id\":\"1\",\"type\":0,\"permission_overwrites\":[]}"
        ).map(json -> Arguments.of(phase, json)));
    }

    @ParameterizedTest
    @MethodSource("invalidFields")
    void actualParserClassifiesInvalidResponseFieldsWithoutRetainingData(HolonetArchiveDiagnostic.Phase phase, String json) {
        setupChannel();
        var rest = new HolonetDiscordChannelRest() {
            @Override protected RestAction<ChannelResponse> request(TextChannel ignored, Route.CompiledRoute route, DataObject body) {
                @SuppressWarnings("unchecked") RestAction<ChannelResponse> action = mock(RestAction.class);
                when(action.complete()).thenReturn(new ChannelResponse(DataObject.fromJson(json)));
                return action;
            }
        };
        assertThatThrownBy(() -> {
            if (phase == GET_PARSE) rest.retrieve(channel);
            else rest.replace(channel, "archive", List.of());
        }).isInstanceOf(HolonetArchiveDiagnostic.Failure.class).satisfies(error -> {
            var failure = (HolonetArchiveDiagnostic.Failure) error;
            assertThat(failure.diagnostic().phase()).isEqualTo(phase);
            assertThat(failure.diagnostic().line()).doesNotContain("synthetic-sensitive");
            assertThat(failure).hasNoCause();
            assertThat(failure.getSuppressed()).isEmpty();
            assertThat(failure.getStackTrace()).isEmpty();
        });
    }

    @ParameterizedTest
    @EnumSource(value = HolonetArchiveDiagnostic.Phase.class, names = {"GET_PARSE", "PATCH_PARSE"})
    void malformedJsonIsCapturedInsideSuccessHandlerWithoutThrowingToJdaRequester(HolonetArchiveDiagnostic.Phase phase) {
        var envelope = HolonetDiscordChannelRest.decode(phase,
                () -> DataObject.fromJson("{synthetic-sensitive-invalid-json"));
        assertThat(envelope.data()).isNull();
        assertThat(envelope.failure().diagnostic().phase()).isEqualTo(phase);
        assertThat(envelope.failure().diagnostic().line()).doesNotContain("synthetic-sensitive");
        assertThat(envelope.failure()).hasNoCause();
        assertThat(envelope.failure().getStackTrace()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = HolonetArchiveDiagnostic.Phase.class, names = {"GET_PARSE", "PATCH_PARSE"})
    void successHandlerDoesNotExposeDecoderExceptionMessagesOrCauses(HolonetArchiveDiagnostic.Phase phase) {
        var envelope = HolonetDiscordChannelRest.decode(phase, () -> {
            throw new IllegalArgumentException("synthetic-sensitive-body", new RuntimeException("synthetic-sensitive-cause"));
        });
        assertThat(envelope.failure().diagnostic().exceptionClass()).isEqualTo(IllegalArgumentException.class.getName());
        assertThat(envelope.failure().getMessage()).doesNotContain("synthetic-sensitive");
        assertThat(envelope.failure()).hasNoCause();
    }

    private void setupChannel() {
        when(channel.getId()).thenReturn("3");
        when(channel.getIdLong()).thenReturn(3L);
        when(channel.getGuild().getIdLong()).thenReturn(1L);
    }

    private static String body(String overrides) {
        return "{\"id\":\"3\",\"guild_id\":\"1\",\"type\":0,\"name\":\"synthetic-sensitive-name\",\"permission_overwrites\":" + overrides + "}";
    }
}
