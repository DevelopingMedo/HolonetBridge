package de.medo.holonetbridge.commons.discord.ticketing;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static de.medo.holonetbridge.commons.discord.ticketing.HolonetArchiveDiagnostic.Phase.*;

import java.util.Set;
import java.util.stream.Stream;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.Request;
import net.dv8tion.jda.api.requests.Response;
import net.dv8tion.jda.api.requests.Route;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.utils.data.DataObject;
import net.dv8tion.jda.internal.JDAImpl;
import net.dv8tion.jda.internal.requests.RestActionImpl;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

class HolonetArchiveRestResponseTest {
    private final TextChannel channel = mock(TextChannel.class);

    private class ExposedRest extends HolonetDiscordChannelRest {
        RestActionImpl<ChannelResponse> action(boolean patch) {
            return (RestActionImpl<ChannelResponse>) request(channel,
                    (patch ? Route.Channels.MODIFY_CHANNEL : Route.Channels.GET_CHANNEL).compile("3"),
                    patch ? DataObject.empty().put("name", "archive") : null);
        }
    }

    static Stream<Arguments> invalidErrorBodies() {
        return Stream.of(false, true).flatMap(patch -> Stream.of(
                "{\"code\":\"synthetic-sensitive-code\",\"message\":\"synthetic-sensitive-message\"}",
                "{\"message\":\"synthetic-sensitive-message\"}", "synthetic-sensitive-not-json")
                .map(body -> Arguments.of(patch, body)));
    }

    static Stream<Arguments> encodedResponses() {
        return Stream.of("gzip", "deflate").flatMap(encoding -> Stream.of(false, true)
                .flatMap(patch -> Stream.of(200, 403).map(status -> Arguments.of(encoding, patch, status))));
    }

    @ParameterizedTest
    @MethodSource("encodedResponses")
    void decodedReaderPreservesGzipAndDeflateWithoutLoggingContentAtTrace(String encoding, boolean patch, int status) throws Exception {
        String json = status == 200 ? "{\"id\":\"3\",\"name\":\"synthetic-sensitive-name\"}"
                : "{\"code\":50013,\"message\":\"synthetic-sensitive-message\",\"errors\":\"synthetic-sensitive-errors\"}";
        var bytes = new ByteArrayOutputStream();
        var deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        try {
            try (var output = encoding.equals("gzip") ? new GZIPOutputStream(bytes) : new DeflaterOutputStream(bytes, deflater)) {
                output.write(json.getBytes(StandardCharsets.UTF_8));
            }
        } finally { deflater.end(); }
        var raw = new okhttp3.Response.Builder().request(new okhttp3.Request.Builder()
                .url("https://discord.invalid/api/channels/3").build())
                .protocol(Protocol.HTTP_1_1).code(status).message("status")
                .header("Content-Encoding", encoding)
                .body(ResponseBody.create(MediaType.parse("application/json"), bytes.toByteArray())).build();
        var envelope = execute(patch, spy(new Response(raw, status, "status", -1, Set.of())));
        assertThat(envelope.toString()).isEqualTo("ArchiveChannelResponse");
        if (status == 200) {
            assertThat(envelope.failure()).isNull();
            assertThat(envelope.data().getLong("id")).isEqualTo(3L);
        } else {
            assertThat(envelope.failure().diagnostic().phase()).isEqualTo(patch ? CHANNEL_PATCH : CHANNEL_GET);
            assertThat(envelope.failure().diagnostic().discordCode()).isEqualTo(50013);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void realActionDoesNotInvokeJdasUnsafeErrorFactoryOrBodyLogger(boolean patch) {
        var response = response(403, "{\"code\":50013,\"message\":\"synthetic-sensitive-message\",\"errors\":\"synthetic-sensitive-errors\"}");
        var envelope = execute(patch, response);
        var diagnostic = envelope.failure().diagnostic();
        assertThat(diagnostic.phase()).isEqualTo(patch ? CHANNEL_PATCH : CHANNEL_GET);
        assertThat(diagnostic.discordError()).isEqualTo(ErrorResponse.MISSING_PERMISSIONS);
        assertThat(diagnostic.discordCode()).isEqualTo(50013);
        assertThat(diagnostic.line()).doesNotContain("synthetic-sensitive");
        assertThat(envelope.failure()).hasNoCause();
        assertThat(envelope.failure().getStackTrace()).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("invalidErrorBodies")
    void malformedHttpErrorBodiesRemainSafeRequestFailures(boolean patch, String body) {
        var envelope = execute(patch, response(400, body));
        assertThat(envelope.failure().diagnostic().phase()).isEqualTo(patch ? CHANNEL_PATCH : CHANNEL_GET);
        assertThat(envelope.failure().diagnostic().discordCode()).isNull();
        assertThat(envelope.failure().diagnostic().line()).doesNotContain("synthetic-sensitive");
        assertThat(envelope.failure()).hasNoCause();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void malformedSuccessfulBodiesAreParseFailuresOutsideTheRequester(boolean patch) {
        var envelope = execute(patch, response(200, "synthetic-sensitive-not-json"));
        assertThat(envelope.failure().diagnostic().phase()).isEqualTo(patch ? PATCH_PARSE : GET_PARSE);
        assertThat(envelope.failure().diagnostic().line()).doesNotContain("synthetic-sensitive");
    }

    @Test
    void successfulBodyUsesTheSameNonLoggingDecoder() {
        var envelope = execute(false, response(200, "{\"id\":\"3\"}"));
        assertThat(envelope.failure()).isNull();
        assertThat(envelope.data().getLong("id")).isEqualTo(3L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void rateLimitsKeepJdasExistingHandling() {
        when(channel.getJDA()).thenReturn(mock(JDAImpl.class));
        var action = new ExposedRest().action(true);
        Request<HolonetDiscordChannelRest.ChannelResponse> request = mock(Request.class);
        var response = new Response(123L, Set.of());
        action.handleResponse(response, request);
        verify(request).onRateLimited(response);
        verifyNoMoreInteractions(request);
    }

    @Test
    void transportFailureRetainsOnlyItsClass() {
        var response = spy(new Response(new IllegalStateException("synthetic-sensitive-transport",
                new RuntimeException("synthetic-sensitive-cause")), Set.of()));
        var envelope = execute(true, response);
        assertThat(envelope.failure().diagnostic().phase()).isEqualTo(CHANNEL_PATCH);
        assertThat(envelope.failure().diagnostic().exceptionClass()).isEqualTo(IllegalStateException.class.getName());
        assertThat(envelope.failure().diagnostic().line()).doesNotContain("synthetic-sensitive");
        assertThat(envelope.failure()).hasNoCause();
    }

    @SuppressWarnings("unchecked")
    private HolonetDiscordChannelRest.ChannelResponse execute(boolean patch, Response response) {
        when(channel.getJDA()).thenReturn(mock(JDAImpl.class));
        var action = new ExposedRest().action(patch);
        Request<HolonetDiscordChannelRest.ChannelResponse> request = mock(Request.class);
        var logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        var logs = new ListAppender<ILoggingEvent>();
        var previousLevel = logger.getLevel();
        logger.setLevel(Level.TRACE);
        logs.start(); logger.addAppender(logs);
        try {
            assertThatCode(() -> action.handleResponse(response, request)).doesNotThrowAnyException();
            var result = ArgumentCaptor.forClass(HolonetDiscordChannelRest.ChannelResponse.class);
            verify(request).onSuccess(result.capture());
            verifyNoMoreInteractions(request);
            verify(response, never()).getObject();
            verify(response, never()).optObject();
            verify(response, never()).getString();
            for (var event : logs.list) {
                assertThat(event.getFormattedMessage()).doesNotContain("synthetic-sensitive");
                assertThat(event.getThrowableProxy()).isNull();
            }
            return result.getValue();
        } finally {
            logger.detachAppender(logs); logs.stop();
            logger.setLevel(previousLevel);
        }
    }

    private Response response(int status, String body) {
        var raw = new okhttp3.Response.Builder().request(new okhttp3.Request.Builder()
                .url("https://discord.invalid/api/channels/3").build())
                .protocol(Protocol.HTTP_1_1).code(status).message("synthetic-sensitive-status")
                .body(ResponseBody.create(MediaType.parse("application/json"), body)).build();
        return spy(new Response(raw, status, "synthetic-sensitive-status", -1, Set.of()));
    }
}
