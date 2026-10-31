package de.medo.holonetbridge.commons.discord.modal;

import de.holonetbridge.commons.message.SupportMessageCodes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

import de.medo.holonetbridge.commons.discord.i18n.I18nProperties;
import de.medo.holonetbridge.commons.discord.i18n.MessageSourceConfiguration;
import de.medo.holonetbridge.commons.domain.HolonetReportFormData;
import de.medo.holonetbridge.commons.domain.HolonetBotInteractionSession;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicketKind;
import de.medo.holonetbridge.commons.extapi.TicketProvider;
import de.medo.holonetbridge.commons.extapi.TicketReference;
import de.medo.holonetbridge.commons.discord.ticketing.*;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.Permission;
import de.medo.holonetbridge.commons.provider.HolonetTicketProviderService;
import de.medo.holonetbridge.gitea.GiteaTicketProvider;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HolonetTicketSubmitterTest {
    private final ModalInteractionEvent event = mock(ModalInteractionEvent.class, RETURNS_DEEP_STUBS);
    private final InteractionHook hook = mock(InteractionHook.class, RETURNS_DEEP_STUBS);
    private final HolonetTicketProviderService providers = mock(HolonetTicketProviderService.class);
    private final TicketProvider provider = mock(TicketProvider.class);
    private final MessageSource messages = new MessageSourceConfiguration().messageSource(new I18nProperties(null));
    private final HolonetChannelCoordinator channels = new HolonetChannelCoordinator();
    private final HolonetTicketInteractionGuard guard = new HolonetTicketInteractionGuard(messages);
    private final HolonetTicketSubmitter submitter = new HolonetTicketSubmitter(providers, messages, channels, guard);
    private final HolonetReportFormData data = new HolonetReportFormData();

    @BeforeEach
    void setUp() {
        when(event.getUserLocale()).thenReturn(DiscordLocale.GERMAN);
        when(event.getGuild().getIdLong()).thenReturn(1L);
        when(event.getUser().getIdLong()).thenReturn(2L);
        when(event.getChannel().getIdLong()).thenReturn(3L);
        when(event.getChannel().getType()).thenReturn(ChannelType.TEXT);
        when(event.getMember().hasPermission(event.getGuildChannel(), Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND)).thenReturn(true);
        var session = new HolonetBotInteractionSession();
        session.setGuildId(1L);
        session.setMemberId(2L);
        data.setSession(session);
        when(provider.create(data)).thenReturn(new TicketReference("gitea", "https://gitea.example.invalid",
                "acme", "support", 11L, 101L, 3L));
        when(providers.providerFor(1L)).thenReturn(Optional.of(provider));
        var reply = event.deferReply(true);
        doAnswer(invocation -> {
            Consumer<InteractionHook> callback = invocation.getArgument(0);
            callback.accept(hook);
            return null;
        }).when(reply).queue(any());
    }

    @Test
    void acknowledgesBeforeCreatingAndDoesNotBlockTheCallbackThread() {
        var callbackThread = Thread.currentThread();
        doAnswer(invocation -> {
            verify(event.deferReply(true)).queue(any());
            assertThat(Thread.currentThread()).isNotSameAs(callbackThread);
            return new TicketReference("gitea", "https://gitea.example.invalid", "acme", "support", 11L, 101L, 3L);
        }).when(provider).create(data);

        submitter.handle(event, data);

        verify(hook, timeout(3000)).editOriginal(messages.getMessage("ticket.created", null, Locale.GERMAN));
        verify(provider, times(1)).create(data);
    }

    @Test
    void staleCroatianSessionAndDiscordLanguagesDoNotOverrideGermanAccountLabels() {
        when(event.getUserLocale()).thenReturn(DiscordLocale.ENGLISH_US);
        when(event.getGuild().getLocale()).thenReturn(DiscordLocale.RUSSIAN);
        var server = useGiteaProvider();
        server.expect(requestTo("https://gitea.example.invalid/api/v1/user"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "token test-token"))
                .andRespond(withSuccess("{\"id\":42,\"language\":\"de-DE\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://gitea.example.invalid/api/v1/repos/acme/support"))
                .andRespond(withSuccess("{\"id\":11,\"full_name\":\"acme/support\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://gitea.example.invalid/api/v1/repos/acme/support/issues"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.body").value(org.hamcrest.Matchers.containsString("Was hast du vor dem Fehler gemacht?")))
                .andExpect(jsonPath("$.body").value(org.hamcrest.Matchers.containsString("Was hättest du erwartet?")))
                .andExpect(jsonPath("$.body").value(org.hamcrest.Matchers.containsString("Was ist stattdessen passiert?")))
                .andExpect(jsonPath("$.body").value(org.hamcrest.Matchers.containsString("Tritt der Fehler erneut auf?")))
                .andExpect(jsonPath("$.body").value(org.hamcrest.Matchers.containsString("Jedes Mal")))
                .andRespond(withSuccess("{\"id\":101,\"number\":3,\"state\":\"open\"}", MediaType.APPLICATION_JSON));

        submitter.handle(event, data);

        verify(hook, timeout(3000)).editOriginal(messages.getMessage("ticket.created", null, Locale.US));
        assertThat(data.getSession().getLocale()).isEqualTo(Locale.of("hr"));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"en-US", "de", "hr", "ru"})
    void accountLookupFailurePreventsIssueAndUsesExistingLocalizedFailure(String language) {
        var locale = DiscordLocale.from(language);
        when(event.getUserLocale()).thenReturn(locale);
        var server = useGiteaProvider();
        server.expect(requestTo("https://gitea.example.invalid/api/v1/user"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).body("test-token reflected"));
        server.expect(ExpectedCount.never(), requestTo("https://gitea.example.invalid/api/v1/repos/acme/support/issues"))
                .andRespond(withStatus(HttpStatus.CREATED));

        submitter.handle(event, data);

        var expected = messages.getMessage("ticket.create-failed", null, locale.toLocale());
        verify(hook, timeout(3000)).editOriginal(expected);
        assertThat(expected).doesNotContain("test-token", "reflected", "403");
        server.verify();
    }

    private MockRestServiceServer useGiteaProvider() {
        var session = new HolonetBotInteractionSession();
        session.setGuildId(1L);
        session.setMemberId(2L);
        session.setLocale(Locale.of("hr"));
        data.setSession(session);
        data.setKind(HolonetTicketKind.BUG);
        data.setTitle("Original title");
        data.setSteps("Original steps");
        data.setExpected("Expected");
        data.setActual("Actual");
        data.setRecurrence("always");
        data.setGuildName("Test server");
        data.setUsername("Tester");
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        when(providers.providerFor(1L)).thenReturn(Optional.of(new GiteaTicketProvider(builder,
                URI.create("https://gitea.example.invalid"), "test-token", "acme", "support", messages)));
        return server;
    }

    @Test
    void reportsMissingConfigurationWithoutCreatingATicket() {
        when(providers.providerFor(1L)).thenReturn(Optional.empty());

        submitter.handle(event, data);

        verify(hook, timeout(3000)).editOriginal(messages.getMessage("ticket.not-configured", null, Locale.GERMAN));
        verifyNoInteractions(provider);
    }

    @Test
    void finalGuardRejectsAnOutstandingModalAfterChannelAccessIsRevoked() {
        channels.withChannel(3, () -> {
            submitter.handle(event, data);
            when(event.getMember().hasPermission(event.getGuildChannel(), Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND)).thenReturn(false);
            return null;
        });
        verify(hook, timeout(3000)).editOriginal(messages.getMessage(SupportMessageCodes.ACTION_UNAVAILABLE, null, Locale.GERMAN));
        verify(provider, never()).create(any());
    }

    @Test
    void failedPostIsReportedWithoutAutomaticRetryOrDurableReservation() {
        when(provider.create(data)).thenThrow(new IllegalStateException("uncertain POST"));
        submitter.handle(event, data);
        verify(hook, timeout(3000)).editOriginal(messages.getMessage("ticket.create-failed", null, Locale.GERMAN));
        verify(provider).create(data);
        submitter.handle(event, data);
        verify(hook, timeout(3000).times(2)).editOriginal(messages.getMessage("ticket.create-failed", null, Locale.GERMAN));
        verify(provider, times(2)).create(data);
    }

    @Test
    void creationAndArchiveAreSerializedThroughRemoteCompletion() throws Exception {
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        when(provider.create(data)).thenAnswer(call -> {
            entered.countDown();
            if (!release.await(3, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("test timeout");
            return new TicketReference("gitea", "https://gitea.example.invalid", "acme", "support", 11L, 101L, 3L);
        });
        submitter.handle(event, data);
        assertThat(entered.await(3, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        var archived = channels.async(3, () -> "archive may now run");
        try {
            assertThat(archived).isNotDone();
        } finally { release.countDown(); }
        assertThat(archived.get(3, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo("archive may now run");
        verify(provider).create(data);
    }

    @Test
    void draftFromAnotherUserCannotBeSubmittedEvenWithChannelAccess() {
        data.getSession().setMemberId(8L);
        submitter.handle(event, data);
        verify(hook, timeout(3000)).editOriginal(messages.getMessage(SupportMessageCodes.ACTION_UNAVAILABLE, null, Locale.GERMAN));
        verify(provider, never()).create(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"en-US", "de", "hr", "ru"})
    void localizesFailureWithoutTechnicalDetailsOrSecrets(String language) {
        var locale = DiscordLocale.from(language);
        when(event.getUserLocale()).thenReturn(locale);
        doThrow(new IllegalStateException("HTTP failed: test-token https://private.invalid"))
                .when(provider).create(data);

        submitter.handle(event, data);

        String expected = messages.getMessage("ticket.create-failed", null, locale.toLocale());
        verify(hook, timeout(3000)).editOriginal(expected);
        assertThat(expected).doesNotContain("test-token", "private.invalid", "HTTP");
        verify(provider, times(1)).create(data);
        assertThat(messages.getMessage("ticket.created", null, locale.toLocale())).isNotBlank();
        assertThat(messages.getMessage("ticket.not-configured", null, locale.toLocale())).contains("/setup");
    }
}
