package de.medo.holonetbridge.commons.discord.modal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

import de.medo.holonetbridge.commons.discord.i18n.I18nProperties;
import de.medo.holonetbridge.commons.discord.i18n.MessageSourceConfiguration;
import de.medo.holonetbridge.commons.domain.HolonetReportFormData;
import de.medo.holonetbridge.commons.extapi.TicketProvider;
import de.medo.holonetbridge.commons.provider.HolonetTicketProviderService;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.MessageSource;

class HolonetTicketSubmitterTest {
    private final ModalInteractionEvent event = mock(ModalInteractionEvent.class, RETURNS_DEEP_STUBS);
    private final InteractionHook hook = mock(InteractionHook.class, RETURNS_DEEP_STUBS);
    private final HolonetTicketProviderService providers = mock(HolonetTicketProviderService.class);
    private final TicketProvider provider = mock(TicketProvider.class);
    private final MessageSource messages = new MessageSourceConfiguration().messageSource(new I18nProperties(null));
    private final HolonetTicketSubmitter submitter = new HolonetTicketSubmitter(providers, messages);
    private final HolonetReportFormData data = new HolonetReportFormData();

    @BeforeEach
    void setUp() {
        when(event.getUserLocale()).thenReturn(DiscordLocale.GERMAN);
        when(event.getGuild().getIdLong()).thenReturn(1L);
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
            return null;
        }).when(provider).create(data);

        submitter.handle(event, data);

        verify(hook, timeout(3000)).editOriginal(messages.getMessage("ticket.created", null, Locale.GERMAN));
        verify(provider, times(1)).create(data);
    }

    @Test
    void reportsMissingConfigurationWithoutCreatingATicket() {
        when(providers.providerFor(1L)).thenReturn(Optional.empty());

        submitter.handle(event, data);

        verify(hook, timeout(3000)).editOriginal(messages.getMessage("ticket.not-configured", null, Locale.GERMAN));
        verifyNoInteractions(provider);
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
