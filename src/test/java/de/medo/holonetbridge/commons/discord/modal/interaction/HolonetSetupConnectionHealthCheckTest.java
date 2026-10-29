package de.medo.holonetbridge.commons.discord.modal.interaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.function.Consumer;

import de.medo.holonetbridge.commons.crypto.EncryptionService;
import de.medo.holonetbridge.commons.discord.i18n.I18nProperties;
import de.medo.holonetbridge.commons.discord.i18n.MessageSourceConfiguration;
import de.medo.holonetbridge.commons.discord.modal.HolonetModalFactory;
import de.medo.holonetbridge.commons.domain.HolonetSetupFormData;
import de.medo.holonetbridge.commons.domain.TicketProvider;
import de.medo.holonetbridge.commons.provider.ProviderHealthCheckException;
import de.medo.holonetbridge.commons.provider.HealthCheckResult;
import de.medo.holonetbridge.commons.provider.ProviderHealthCheckException.Reason;
import de.medo.holonetbridge.commons.provider.ProviderHealthCheckService;
import de.medo.holonetbridge.commons.service.HolonetBotInteractionSessionService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
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
    private final HolonetSetupConnectionSubmitModalAction action =
            new HolonetSetupConnectionSubmitModalAction(messages, sessions, encryption, healthChecks);

    @BeforeEach
    void setUp() {
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
        when(sessions.getOrCreateSetupFormDataSession(anyLong(), anyLong(), any()))
                .thenReturn(formData);
        ReplyCallbackAction reply = event.reply("Teste Verbindungsdaten …").setEphemeral(true);
        doAnswer(invocation -> {
            Consumer<InteractionHook> callback = invocation.getArgument(0);
            callback.accept(hook);
            return null;
        }).when(reply).queue(any());
    }

    @Test
    void acknowledgesPrivatelyBeforeCheckingAndRepliesWithSuccess() {
        doAnswer(invocation -> {
            verify(event.reply("Teste Verbindungsdaten …").setEphemeral(true)).queue(any());
            return null;
        }).when(healthChecks).check(TicketProvider.GITEA, "https://gitea.example.invalid", "test-token");

        action.handle(event);

        verify(hook, timeout(3000)).editOriginal("Basis-URL und Token für Gitea wurden erfolgreich geprüft.");
        verify(supportChannel, timeout(3000))
                .sendMessage("Du brauchst Unterstützung? Klicke unten, um eine Support-Anfrage zu erstellen.");
        verify(healthChecks).check(TicketProvider.GITEA, "https://gitea.example.invalid", "test-token");
        verify(sessions).saveSetupFormDataSession(formData);
        verify(sessions, timeout(3000)).completeSetup(eq(formData), argThat(HealthCheckResult::successful));
        assertThat(formData.getToken()).isEqualTo("encrypted-test-token");
    }

    @ParameterizedTest
    @EnumSource(Reason.class)
    void repliesWithAReadableErrorAndSetupRetryForEveryExpectedFailure(Reason reason) {
        doThrow(new ProviderHealthCheckException(reason)).when(healthChecks).check(any(), any(), any());

        action.handle(event);

        ArgumentCaptor<String> reply = ArgumentCaptor.forClass(String.class);
        verify(hook, timeout(3000)).editOriginal(reply.capture());
        assertThat(reply.getValue()).contains("/setup").doesNotContain("test-token", "erfolgreich");
        verify(supportChannel, never()).sendMessage(anyString());
        verify(sessions, timeout(3000)).completeSetup(eq(formData), argThat(result -> !result.successful()));
    }

    @Test
    void returnsAGenericErrorWithoutExposingUnexpectedExceptionDetails() {
        doThrow(new IllegalStateException("reflected test-token")).when(healthChecks).check(any(), any(), any());

        action.handle(event);

        verify(hook, timeout(3000)).editOriginal("Der Verbindungstest ist fehlgeschlagen. Bitte /setup erneut ausführen.");
        verify(supportChannel, never()).sendMessage(anyString());
        verify(sessions, timeout(3000)).completeSetup(eq(formData), argThat(result -> !result.successful()));
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

        verify(hook, timeout(3000)).editOriginal("Basis-URL und Token für Gitea wurden erfolgreich geprüft.");
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
        assertThat(formData.getToken()).isEqualTo("encrypted-empty-token");
    }

    @Test
    void completionFailureIsCaughtInsideTheAsynchronousCallback() {
        doThrow(new IllegalStateException("test-token"))
                .when(sessions).completeSetup(eq(formData), any(HealthCheckResult.class));

        action.handle(event);

        verify(hook, timeout(3000)).editOriginal("Der Verbindungstest ist fehlgeschlagen. Bitte /setup erneut ausführen.");
        verify(supportChannel, never()).sendMessage(anyString());
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
        assertThat(messages.getMessage("support.panel-message", null, DiscordLocale.from(language).toLocale()))
                .isNotBlank();
        assertThat(messages.getMessage("support.open-button", null, DiscordLocale.from(language).toLocale()))
                .isNotBlank();
    }
}
