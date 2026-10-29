package de.medo.holonetbridge.commons.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

import de.medo.holonetbridge.commons.domain.*;
import de.medo.holonetbridge.commons.repository.*;
import org.junit.jupiter.api.Test;
import de.medo.holonetbridge.commons.provider.HealthCheckResult;
import org.springframework.test.util.ReflectionTestUtils;

class HolonetBotInteractionSessionServiceTest {
    private final HolonetBotInteractionSessionRepository sessions = mock(HolonetBotInteractionSessionRepository.class);
    private final HolonetSetupFormDataRepository forms = mock(HolonetSetupFormDataRepository.class);
    private final HolonetDiscordConfigRepository configs = mock(HolonetDiscordConfigRepository.class);
    private final HolonetBotInteractionSessionService service = new HolonetBotInteractionSessionService(
            sessions, forms, mock(HolonetReportFormDataRepository.class), configs);

    private HolonetSetupFormData draft() {
        var session = new HolonetBotInteractionSession();
        session.setId(42L);
        session.setGuildId(100L);
        session.setMemberId(200L);
        var form = new HolonetSetupFormData();
        form.setSession(session);
        form.setBaseUrl("https://draft.example");
        when(forms.findLatestOpenSetupFormData(eq(100L), eq(200L), any()))
                .thenReturn(Optional.of(form));
        return form;
    }

    @Test
    void savedConfigWinsOverAnActivePersonalDraft() {
        var config = new HolonetDiscordConfig();
        config.setGuildId(100L);
        config.setBaseUrl("https://saved.example");
        config.setTicketProvider(TicketProvider.GITEA);
        config.setTarget("acme/support");
        config.setToken("encrypted");
        config.setSupportChannelId(99L);
        when(configs.findById(100L)).thenReturn(Optional.of(config));

        HolonetSetupFormData opened = service.getOrCreateSetupFormDataSession(100, 200, Locale.GERMAN);

        assertThat(opened.getId()).isNull();
        assertThat(opened.getSession().getId()).isNull();
        verifyNoInteractions(forms, sessions);
        assertThat(opened.getSession().getLocale()).isEqualTo(Locale.GERMAN);
        assertThat(opened.getBaseUrl()).isEqualTo(config.getBaseUrl());
        assertThat(opened.getToken()).isEqualTo("encrypted");
        assertThat(opened.getTicketProvider()).isEqualTo(TicketProvider.GITEA);
        assertThat(opened.getTarget()).isEqualTo("acme/support");
        assertThat(opened.getSupportChannelId()).isEqualTo(99L);
    }

    @Test
    void withoutConfigResumesDraftAndRefreshesActivity() {
        HolonetSetupFormData form = draft();
        Instant previous = Instant.now().minusSeconds(600);
        ReflectionTestUtils.setField(form.getSession(), "updatedAt", previous);
        assertThat(service.getOrCreateSetupFormDataSession(100, 200, Locale.ENGLISH)).isSameAs(form);
        assertThat(form.getBaseUrl()).isEqualTo("https://draft.example");
        assertThat(form.getSession().getUpdatedAt()).isAfter(previous);
    }

    @Test
    void expiredDraftIsClosedAndNewDraftHasNoInvocationChannel() {
        HolonetSetupFormData old = draft();
        ReflectionTestUtils.setField(old.getSession(), "updatedAt", Instant.now().minus(Duration.ofHours(2)));
        when(sessions.save(any())).thenAnswer(i -> i.getArgument(0));
        when(forms.save(any())).thenAnswer(i -> i.getArgument(0));

        HolonetSetupFormData opened = service.getOrCreateSetupFormDataSession(100, 200, Locale.GERMAN);

        assertThat(old.getSession().getClosedAt()).isNotNull();
        assertThat(opened.getSession()).isNotSameAs(old.getSession());
        assertThat(opened.getSession().getChannelId()).isNull();
        assertThat(opened.getSession().getGuildId()).isEqualTo(100L);
        assertThat(opened.getSession().getMemberId()).isEqualTo(200L);
        assertThat(opened.getBaseUrl()).isNull();
    }

    @Test
    void savingDraftDoesNotChangeConfigAndCompletionClosesSession() {
        HolonetSetupFormData form = draft();
        when(sessions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service.saveSetupFormDataSession(form);
        verify(configs, never()).save(any());
        service.completeSetup(form, new HealthCheckResult("ok", true));
        org.mockito.ArgumentCaptor<HolonetDiscordConfig> capture = org.mockito.ArgumentCaptor.forClass(HolonetDiscordConfig.class);
        verify(configs).save(capture.capture());
        assertThat(capture.getValue().getGuildId()).isEqualTo(100L);
        assertThat(capture.getValue().getBaseUrl()).isEqualTo(form.getBaseUrl());
        assertThat(form.getSession().getClosedAt()).isNotNull();
    }
    @Test
    void failedHealthCheckDoesNotSaveConfigurationOrCloseSession() {
        var form = new HolonetSetupFormData();
        var session = new HolonetBotInteractionSession();
        form.setSession(session);

        service.completeSetup(form, new HealthCheckResult("failed", false));

        verifyNoInteractions(configs, sessions, forms);
        assertThat(session.getClosedAt()).isNull();
    }

    @Test
    void unchangedConfigCanBeConfirmedWithoutSavingFormOrSession() {
        var config = new HolonetDiscordConfig();
        config.setGuildId(100L);
        config.setTicketProvider(TicketProvider.GITEA);
        config.setBaseUrl("https://saved.example");
        when(configs.findById(100L)).thenReturn(Optional.of(config));
        HolonetSetupFormData form = service.getOrCreateSetupFormDataSession(100, 200, Locale.GERMAN);

        service.completeSetup(form, new HealthCheckResult("ok", true));

        verify(configs).save(config);
        verifyNoInteractions(forms, sessions);
        assertThat(form.getSession().getId()).isNull();
    }

}
