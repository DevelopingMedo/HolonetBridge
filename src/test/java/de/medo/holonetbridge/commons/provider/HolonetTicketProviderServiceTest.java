package de.medo.holonetbridge.commons.provider;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import de.medo.holonetbridge.commons.crypto.EncryptionService;
import de.medo.holonetbridge.commons.domain.HolonetBotInteractionSession;
import de.medo.holonetbridge.commons.domain.HolonetDiscordConfig;
import de.medo.holonetbridge.commons.domain.HolonetReportFormData;
import de.medo.holonetbridge.commons.domain.HolonetSetupFormData;
import de.medo.holonetbridge.commons.extapi.TicketProvider;
import de.medo.holonetbridge.commons.repository.HolonetDiscordConfigRepository;
import de.medo.holonetbridge.commons.service.HolonetBotInteractionSessionService;
import de.medo.holonetbridge.gitea.GiteaProviderFactory;
import org.junit.jupiter.api.Test;

class HolonetTicketProviderServiceTest {
    private final HolonetDiscordConfigRepository configs = mock(HolonetDiscordConfigRepository.class);
    private final HolonetBotInteractionSessionService sessions = mock(HolonetBotInteractionSessionService.class);
    private final EncryptionService encryption = mock(EncryptionService.class);
    private final GiteaProviderFactory factory = mock(GiteaProviderFactory.class);
    private final HolonetTicketProviderService service =
            new HolonetTicketProviderService(configs, sessions, encryption, factory);
    private final HealthCheckResult success = new HealthCheckResult("ok", true);

    @Test
    void reusesClientsAcrossTicketsAndSeparatesGuildsAndRotation() {
        var first = mock(TicketProvider.class);
        var second = mock(TicketProvider.class);
        var rotated = mock(TicketProvider.class);
        when(encryption.decrypt("ciphertext")).thenReturn("test-token");
        when(factory.create("gitea.example.invalid", "test-token", "acme/support"))
                .thenReturn(first, second, rotated);
        var form = form(1);
        service.completeSetup(form, success, service.candidateFor(form));
        var otherForm = form(2);
        service.completeSetup(otherForm, success, service.candidateFor(otherForm));

        var report = new HolonetReportFormData();
        service.providerFor(1).orElseThrow().create(report);
        service.providerFor(1).orElseThrow().create(report);

        verify(first, times(2)).create(report);
        verify(factory, times(2)).create(anyString(), anyString(), anyString());
        var candidate = service.candidateFor(form);
        assertThat(service.providerFor(1)).containsSame(first);
        service.completeSetup(form, success, candidate);
        assertThat(service.providerFor(1)).containsSame(rotated);
        assertThat(service.providerFor(2)).containsSame(second);
        assertThat(service.providerFor(3)).isEmpty();
    }

    @Test
    void keepsOldProviderDuringPersistenceAndAfterCommitFailure() {
        var form = form(1);
        var oldProvider = mock(TicketProvider.class);
        var candidate = mock(TicketProvider.class);
        service.completeSetup(form, success, oldProvider);
        doAnswer(invocation -> {
            assertThat(service.providerFor(1)).containsSame(oldProvider);
            throw new IllegalStateException("commit failed");
        }).when(sessions).completeSetup(form, success);

        assertThatThrownBy(() -> service.completeSetup(form, success, candidate))
                .hasMessage("commit failed");

        assertThat(service.providerFor(1)).containsSame(oldProvider);
    }

    @Test
    void restoresOnlyActiveConfigsAndSkipsCorruptRowsWithoutNetworking() {
        when(configs.findAllGuildIds()).thenReturn(List.of(1L, 2L, 3L, 4L));
        when(configs.findById(1L)).thenReturn(Optional.of(config(1)));
        when(configs.findById(2L)).thenThrow(new IllegalArgumentException("invalid stored enum"));
        var invalid = config(3);
        invalid.setToken("corrupt");
        when(configs.findById(3L)).thenReturn(Optional.of(invalid));
        when(configs.findById(4L)).thenReturn(Optional.of(config(4)));
        when(encryption.decrypt("corrupt")).thenThrow(new IllegalArgumentException("invalid ciphertext"));
        when(encryption.decrypt("ciphertext")).thenReturn("test-token");
        var first = mock(TicketProvider.class);
        var last = mock(TicketProvider.class);
        when(factory.create(anyString(), anyString(), anyString())).thenReturn(first, last);

        service.restoreActiveProviders();

        assertThat(service.providerFor(1)).containsSame(first);
        assertThat(service.providerFor(2)).isEmpty();
        assertThat(service.providerFor(3)).isEmpty();
        assertThat(service.providerFor(4)).containsSame(last);
        verifyNoInteractions(sessions, first, last);
        verify(factory, times(2)).create(anyString(), anyString(), anyString());
    }

    @Test
    void serializesCommitAndActivationForConcurrentSetups() throws Exception {
        var first = mock(TicketProvider.class);
        var second = mock(TicketProvider.class);
        var form = form(1);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(3, TimeUnit.SECONDS)).isTrue();
            return null;
        }).doAnswer(invocation -> {
            assertThat(service.providerFor(1)).containsSame(first);
            return null;
        }).when(sessions).completeSetup(form, success);
        var firstCompletion = CompletableFuture.runAsync(() -> service.completeSetup(form, success, first));
        assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
        var secondStarted = new CountDownLatch(1);
        var secondCompletion = CompletableFuture.runAsync(() -> {
            secondStarted.countDown();
            service.completeSetup(form, success, second);
        });
        try {
            assertThat(secondStarted.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(service.providerFor(1)).isEmpty();
        } finally {
            release.countDown();
        }
        CompletableFuture.allOf(firstCompletion, secondCompletion).get(3, TimeUnit.SECONDS);
        assertThat(service.providerFor(1)).containsSame(second);
    }

    private HolonetDiscordConfig config(long guildId) {
        var config = new HolonetDiscordConfig();
        config.setGuildId(guildId);
        config.setTicketProvider(de.medo.holonetbridge.commons.domain.TicketProvider.GITEA);
        config.setBaseUrl("gitea.example.invalid");
        config.setTarget("acme/support");
        config.setToken("ciphertext");
        return config;
    }

    private HolonetSetupFormData form(long guildId) {
        var form = new HolonetSetupFormData();
        var session = new HolonetBotInteractionSession();
        session.setGuildId(guildId);
        form.setSession(session);
        form.setTicketProvider(de.medo.holonetbridge.commons.domain.TicketProvider.GITEA);
        form.setBaseUrl("gitea.example.invalid");
        form.setTarget("acme/support");
        form.setToken("ciphertext");
        return form;
    }
}
