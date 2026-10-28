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
import de.medo.holonetbridge.commons.provider.ProviderHealthCheckException.Reason;
import de.medo.holonetbridge.commons.provider.ProviderHealthCheckService;
import de.medo.holonetbridge.commons.service.HolonetBotInteractionSessionService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionHook;
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
    private final HolonetSetupFormData formData = new HolonetSetupFormData();
    private final MessageSource messages = new MessageSourceConfiguration().messageSource(new I18nProperties(null));
    private final HolonetSetupConnectionSubmitModalAction action =
            new HolonetSetupConnectionSubmitModalAction(messages, sessions, encryption, healthChecks);

    @BeforeEach
    void setUp() {
        when(event.getUserLocale()).thenReturn(DiscordLocale.GERMAN);
        when(event.getGuild().getIdLong()).thenReturn(1L);
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
        when(sessions.getOrCreateSetupFormDataSession(anyLong(), anyLong(), anyLong(), any()))
                .thenReturn(formData);
        var deferredReply = event.deferReply(true);
        doAnswer(invocation -> {
            Consumer<InteractionHook> callback = invocation.getArgument(0);
            callback.accept(hook);
            return null;
        }).when(deferredReply).queue(any());
    }

    @Test
    void acknowledgesPrivatelyBeforeCheckingAndRepliesWithSuccess() {
        doAnswer(invocation -> {
            verify(event.deferReply(true)).queue(any());
            return null;
        }).when(healthChecks).check(TicketProvider.GITEA, "https://gitea.example.invalid", "test-token");

        action.handle(event);

        verify(hook, timeout(3000)).editOriginal("Basis-URL und Token für Gitea wurden erfolgreich geprüft.");
        verify(healthChecks).check(TicketProvider.GITEA, "https://gitea.example.invalid", "test-token");
        verify(sessions).saveSetupFormDataSession(formData);
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
    }

    @Test
    void returnsAGenericErrorWithoutExposingUnexpectedExceptionDetails() {
        doThrow(new IllegalStateException("reflected test-token")).when(healthChecks).check(any(), any(), any());

        action.handle(event);

        verify(hook, timeout(3000)).editOriginal("Der Verbindungstest ist fehlgeschlagen. Bitte /setup erneut ausführen.");
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
    }
}
