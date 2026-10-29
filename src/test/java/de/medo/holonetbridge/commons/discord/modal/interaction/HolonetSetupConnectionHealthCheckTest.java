package de.medo.holonetbridge.commons.discord.modal.interaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import de.medo.holonetbridge.commons.crypto.EncryptionService;
import de.medo.holonetbridge.commons.discord.i18n.I18nProperties;
import de.medo.holonetbridge.commons.discord.i18n.MessageSourceConfiguration;
import de.medo.holonetbridge.commons.discord.modal.HolonetModalFactory;
import de.medo.holonetbridge.commons.domain.HolonetSetupFormData;
import de.medo.holonetbridge.commons.domain.HolonetBotInteractionSession;
import de.medo.holonetbridge.commons.domain.TicketProvider;
import de.medo.holonetbridge.commons.provider.ProviderHealthCheckException;
import de.medo.holonetbridge.commons.provider.HealthCheckResult;
import de.medo.holonetbridge.commons.provider.ProviderHealthCheckException.Reason;
import de.medo.holonetbridge.commons.provider.ProviderHealthCheckService;
import de.medo.holonetbridge.commons.provider.HolonetTicketProviderService;
import de.medo.holonetbridge.commons.repository.HolonetDiscordConfigRepository;
import de.medo.holonetbridge.commons.provider.ProviderFactory;
import de.medo.holonetbridge.commons.service.HolonetBotInteractionSessionService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.MessageSource;

class HolonetSetupConnectionHealthCheckTest {

    private final ModalInteractionEvent event = mock(ModalInteractionEvent.class, RETURNS_DEEP_STUBS);
    private final InteractionHook hook = mock(InteractionHook.class, RETURNS_DEEP_STUBS);
    private final HolonetBotInteractionSessionService sessions = mock(HolonetBotInteractionSessionService.class);
    private final EncryptionService encryption = mock(EncryptionService.class);
    private final ProviderHealthCheckService healthChecks = mock(ProviderHealthCheckService.class);
    private final TextChannel supportChannel = mock(TextChannel.class, RETURNS_DEEP_STUBS);
    private final HolonetSetupFormData formData = new HolonetSetupFormData();
    private final MessageSource messages = new MessageSourceConfiguration().messageSource(new I18nProperties(null));
    private final ProviderFactory providerFactory = mock(ProviderFactory.class);
    private final de.medo.holonetbridge.commons.extapi.TicketProvider candidate =
            mock(de.medo.holonetbridge.commons.extapi.TicketProvider.class);
    private final HolonetTicketProviderService providers = new HolonetTicketProviderService(
            mock(HolonetDiscordConfigRepository.class), sessions, providerFactory);
    private final HolonetSetupConnectionSubmitModalAction action =
            new HolonetSetupConnectionSubmitModalAction(messages, sessions, encryption, healthChecks, providers);

    private final MessageCreateAction panelAction = mock(MessageCreateAction.class, RETURNS_SELF);
    private final CompletableFuture<net.dv8tion.jda.api.entities.Message> panelPost = new CompletableFuture<>();
    private static final String COMPLETED = "✅ Setup abgeschlossen. Das Support-Panel wurde in <#4> veröffentlicht.";

    @BeforeEach
    void setUp() {
        var session = new HolonetBotInteractionSession();
        session.setGuildId(1L);
        session.setMemberId(2L);
        session.setLocale(Locale.GERMAN);
        formData.setSession(session);
        when(supportChannel.sendMessage(anyString())).thenReturn(panelAction);
        when(panelAction.submit()).thenReturn(panelPost);
        when(supportChannel.getAsMention()).thenReturn("<#4>");
        when(event.getUserLocale()).thenReturn(DiscordLocale.GERMAN);
        when(event.getGuild().getIdLong()).thenReturn(1L);
        when(event.getGuild().getLocale()).thenReturn(DiscordLocale.GERMAN);
        when(event.getGuild().getTextChannelById(4L)).thenReturn(supportChannel);
        when(event.getMember().getIdLong()).thenReturn(2L);
        when(event.getChannel().getIdLong()).thenReturn(3L);
        when(event.getMember().hasPermission(Permission.MANAGE_SERVER)).thenReturn(true);
        when(event.getValue(HolonetModalFactory.STRINGSELECTMENU_ID_PROVIDER).getAsStringList())
                .thenReturn(List.of("GITEA"));
        when(event.getValue(HolonetModalFactory.ENTITYSELECTMENU_ID_CHANNEL).getAsLongList())
                .thenReturn(List.of(4L));
        when(event.getValue(HolonetModalFactory.TEXTINPUT_ID_BASE_URL).getAsString())
                .thenReturn(" https://gitea.example.invalid ");
        when(event.getValue(HolonetModalFactory.TEXTINPUT_ID_TARGET).getAsString()).thenReturn("acme/support");
        when(event.getValue(HolonetModalFactory.TEXTINPUT_ID_TOKEN).getAsString()).thenReturn("test-token");
        when(encryption.encrypt("test-token")).thenReturn("encrypted-test-token");
        when(encryption.decrypt("encrypted-test-token")).thenReturn("test-token");
        when(providerFactory.create(any(), anyString(), any(), anyString())).thenReturn(candidate);
        when(sessions.getOrCreateSetupFormDataSession(anyLong(), anyLong(), any()))
                .thenReturn(formData);
        ReplyCallbackAction reply = event.deferReply(true);
        doAnswer(invocation -> {
            Consumer<InteractionHook> callback = invocation.getArgument(0);
            callback.accept(hook);
            return null;
        }).when(reply).queue(any());
    }

    @Test
    void acknowledgesPrivatelyBeforeCheckingAndRepliesWithSuccess() {
        doAnswer(invocation -> {
            verify(event.deferReply(true)).queue(any());
            verify(hook).editOriginal("Verbindungsdaten gespeichert.\nTeste Verbindungsdaten …");
            return null;
        }).when(healthChecks).check(TicketProvider.GITEA, "https://gitea.example.invalid", "test-token");

        action.handle(event);

        verify(panelAction, timeout(3000)).submit();
        panelPost.complete(mock(net.dv8tion.jda.api.entities.Message.class));
        verify(hook, timeout(3000)).editOriginal(COMPLETED);
        verify(supportChannel, timeout(3000))
                .sendMessage("Du brauchst Unterstützung? Klicke unten, um eine Support-Anfrage zu erstellen.");
        verify(healthChecks).check(TicketProvider.GITEA, "https://gitea.example.invalid", "test-token");
        ArgumentCaptor<HolonetSetupFormData> saved = ArgumentCaptor.forClass(HolonetSetupFormData.class);
        verify(sessions).saveSetupFormDataSession(saved.capture());
        verify(sessions, timeout(3000)).completeSetup(eq(saved.getValue()), argThat(HealthCheckResult::successful));
        assertThat(saved.getValue().getToken()).isEqualTo("encrypted-test-token");
        assertThat(formData.getToken()).isNull();
        assertThat(providers.providerFor(1L)).containsSame(candidate);
    }

    @ParameterizedTest
    @EnumSource(Reason.class)
    void repliesWithAReadableErrorAndSetupRetryForEveryExpectedFailure(Reason reason) {
        doThrow(new ProviderHealthCheckException(reason)).when(healthChecks).check(any(), any(), any());

        action.handle(event);

        ArgumentCaptor<String> reply = ArgumentCaptor.forClass(String.class);
        verify(hook, timeout(3000)).editOriginal(argThat((String text) -> text.contains("/setup")));
        verify(hook, atLeastOnce()).editOriginal(reply.capture());
        assertThat(reply.getAllValues().getLast()).contains("/setup").doesNotContain("test-token", "erfolgreich");
        verify(supportChannel, never()).sendMessage(anyString());
        verify(sessions, never()).completeSetup(any(), any());
        verifyNoInteractions(providerFactory);
    }

    @Test
    void unchangedValuesAreNotEncryptedOrSavedAgain() {
        formData.setTicketProvider(TicketProvider.GITEA);
        formData.setBaseUrl("https://gitea.example.invalid");
        formData.setTarget("acme/support");
        formData.setSupportChannelId(4L);
        formData.setToken("existing-ciphertext");
        when(encryption.decrypt("existing-ciphertext")).thenReturn("test-token");

        action.handle(event);

        verify(panelAction, timeout(3000)).submit();
        panelPost.complete(mock(net.dv8tion.jda.api.entities.Message.class));
        verify(hook, timeout(3000)).editOriginal(COMPLETED);
        verify(encryption, never()).encrypt(anyString());
        verify(sessions, never()).saveSetupFormDataSession(any());
        verify(sessions).completeSetup(eq(formData), argThat(HealthCheckResult::successful));
        assertThat(formData.getToken()).isEqualTo("existing-ciphertext");
    }

    @Test
    void emptyModalTokenIsUsedInsteadOfThePreviouslySavedToken() {
        formData.setToken("existing-ciphertext");
        when(encryption.decrypt("existing-ciphertext")).thenReturn("previous-token");
        when(event.getValue(HolonetModalFactory.TEXTINPUT_ID_TOKEN).getAsString()).thenReturn("");
        when(encryption.encrypt("")).thenReturn("encrypted-empty-token");

        action.handle(event);

        verify(healthChecks, timeout(3000)).check(TicketProvider.GITEA, "https://gitea.example.invalid", "");
        ArgumentCaptor<HolonetSetupFormData> saved = ArgumentCaptor.forClass(HolonetSetupFormData.class);
        verify(sessions).saveSetupFormDataSession(saved.capture());
        assertThat(saved.getValue().getToken()).isEqualTo("encrypted-empty-token");
    }

    @Test
    void waitsForPanelPostBeforeSavingAndConfirmingSetup() {
        action.handle(event);

        verify(panelAction, timeout(3000)).submit();
        verify(sessions, never()).completeSetup(any(), any());
        verify(hook, never()).editOriginal(COMPLETED);
        assertThat(providers.providerFor(1L)).isEmpty();
        verify(providerFactory).create(TicketProvider.GITEA,
                "https://gitea.example.invalid", "encrypted-test-token", "acme/support");

        panelPost.complete(mock(net.dv8tion.jda.api.entities.Message.class));

        verify(hook, timeout(3000)).editOriginal(COMPLETED);
        verify(sessions).completeSetup(any(), argThat(HealthCheckResult::successful));
    }

    @Test
    void failedPanelPostDoesNotCompleteSetup() {
        action.handle(event);
        verify(panelAction, timeout(3000)).submit();

        panelPost.completeExceptionally(new IllegalStateException("Discord unavailable"));

        verify(hook, timeout(3000)).editOriginal(messages.getMessage("setup.panel.post-failed", null, Locale.GERMAN));
        verify(sessions, never()).completeSetup(any(), any());
        assertThat(providers.providerFor(1L)).isEmpty();
    }

    @Test
    void missingChannelDoesNotCompleteSetup() {
        when(event.getGuild().getTextChannelById(4L)).thenReturn(null);

        action.handle(event);

        verify(hook, timeout(3000)).editOriginal(messages.getMessage("setup.panel.channel-unavailable", null, Locale.GERMAN));
        verify(sessions, never()).completeSetup(any(), any());
        verify(panelAction, never()).submit();
    }

    @Test
    void failedPersistenceDoesNotConfirmSetup() {
        doThrow(new IllegalStateException("Database unavailable"))
                .when(sessions).completeSetup(any(), any());
        action.handle(event);
        verify(panelAction, timeout(3000)).submit();

        panelPost.complete(mock(net.dv8tion.jda.api.entities.Message.class));

        verify(sessions, timeout(3000)).completeSetup(any(), any());
        verify(hook, timeout(3000)).editOriginal(messages.getMessage("setup.failed", null, Locale.GERMAN));
        verify(hook, never()).editOriginal(COMPLETED);
        assertThat(providers.providerFor(1L)).isEmpty();
    }

    @Test
    void candidateFailureDoesNotPostPanelOrChangeTheActiveProvider() {
        when(providerFactory.create(any(), anyString(), any(), anyString()))
                .thenThrow(new IllegalArgumentException("test-token reflected"));

        action.handle(event);

        verify(hook, timeout(3000)).editOriginal(messages.getMessage("setup.failed", null, Locale.GERMAN));
        verify(panelAction, never()).submit();
        verify(sessions, never()).completeSetup(any(), any());
        assertThat(providers.providerFor(1L)).isEmpty();
    }

    @Test
    void persistenceRunsOffThePanelCallbackThread() {
        var callbackThread = Thread.currentThread();
        var persistenceThread = new java.util.concurrent.atomic.AtomicReference<Thread>();
        doAnswer(invocation -> {
            persistenceThread.set(Thread.currentThread());
            return null;
        }).when(sessions).completeSetup(any(), any());
        action.handle(event);
        verify(panelAction, timeout(3000)).submit();

        panelPost.complete(mock(net.dv8tion.jda.api.entities.Message.class));

        verify(hook, timeout(3000)).editOriginal(COMPLETED);
        assertThat(persistenceThread.get()).isNotNull().isNotSameAs(callbackThread);
    }

    @ParameterizedTest
    @ValueSource(strings = {"healthcheck", "candidate", "panel", "persistence", "draft"})
    void failedSetupKeepsThePreviousActiveConnection(String stage) {
        var previous = mock(de.medo.holonetbridge.commons.extapi.TicketProvider.class);
        providers.completeSetup(formData, new HealthCheckResult("ok", true), previous);
        clearInvocations(sessions);
        switch (stage) {
            case "healthcheck" -> doThrow(new ProviderHealthCheckException(Reason.ACCESS))
                    .when(healthChecks).check(any(), any(), any());
            case "candidate" -> when(providerFactory.create(any(), anyString(), any(), anyString()))
                    .thenThrow(new IllegalArgumentException("invalid target"));
            case "panel" -> panelPost.completeExceptionally(new IllegalStateException("post failed"));
            case "persistence" -> doThrow(new IllegalStateException("commit failed"))
                    .when(sessions).completeSetup(any(), any());
            case "draft" -> doThrow(new IllegalStateException("save failed"))
                    .when(sessions).saveSetupFormDataSession(any());
            default -> throw new AssertionError(stage);
        }

        action.handle(event);
        if (stage.equals("persistence")) {
            verify(panelAction, timeout(3000)).submit();
            panelPost.complete(mock(net.dv8tion.jda.api.entities.Message.class));
        }

        String key = switch (stage) {
            case "healthcheck" -> "setup.healthcheck.access";
            case "panel" -> "setup.panel.post-failed";
            default -> "setup.failed";
        };
        verify(hook, timeout(3000)).editOriginal(messages.getMessage(key, new Object[] {"Gitea"}, Locale.GERMAN));
        assertThat(providers.providerFor(1L)).containsSame(previous);
        verify(hook, never()).editOriginal(COMPLETED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"en-US", "de", "hr", "ru"})
    void localizesSuccessAndEveryError(String language) {
        for (Reason reason : Reason.values()) {
            String key = "setup.healthcheck." + reason.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
            assertThat(messages.getMessage(key, new Object[] {"Gitea"}, DiscordLocale.from(language).toLocale()))
                    .contains("/setup");
        }
        assertThat(messages.getMessage("setup.healthcheck.success", new Object[] {"Gitea"},
                DiscordLocale.from(language).toLocale())).contains("Gitea");
        assertThat(messages.getMessage("setup.completed", new Object[] {"<#4>"},
                DiscordLocale.from(language).toLocale())).contains("<#4>");
        assertThat(messages.getMessage("support.panel-message", null, DiscordLocale.from(language).toLocale()))
                .isNotBlank();
        assertThat(messages.getMessage("support.open-button", null, DiscordLocale.from(language).toLocale()))
                .isNotBlank();
        assertThat(messages.getMessage("setup.failed", null, DiscordLocale.from(language).toLocale()))
                .contains("/setup");
    }
}
