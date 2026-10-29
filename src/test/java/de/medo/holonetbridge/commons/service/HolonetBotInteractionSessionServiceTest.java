package de.medo.holonetbridge.commons.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Locale;
import java.util.Optional;

import de.medo.holonetbridge.commons.domain.HolonetBotInteractionSession;
import de.medo.holonetbridge.commons.domain.HolonetSetupFormData;
import de.medo.holonetbridge.commons.repository.HolonetBotInteractionSessionRepository;
import de.medo.holonetbridge.commons.repository.HolonetReportFormDataRepository;
import de.medo.holonetbridge.commons.repository.HolonetSetupFormDataRepository;
import org.junit.jupiter.api.Test;

class HolonetBotInteractionSessionServiceTest {

    private final HolonetBotInteractionSessionRepository sessionRepository =
            mock(HolonetBotInteractionSessionRepository.class);
    private final HolonetSetupFormDataRepository formDataRepository = mock(HolonetSetupFormDataRepository.class);
    private final HolonetReportFormDataRepository reportFormDataRepository = mock(HolonetReportFormDataRepository.class);
    private final HolonetBotInteractionSessionService service =
            new HolonetBotInteractionSessionService(sessionRepository, formDataRepository, reportFormDataRepository);

    @Test
    void returnsFormDataForTheMembersActiveGuildSession() {
        HolonetBotInteractionSession session = new HolonetBotInteractionSession();
        session.setId(42L);
        HolonetSetupFormData formData = new HolonetSetupFormData();
        when(sessionRepository.findBy(100L, 200L, null)).thenReturn(Optional.of(session));
        when(formDataRepository.findBy(42L)).thenReturn(Optional.of(formData));

        assertThat(service.getOrCreateSetupFormDataSession(100L, 200L, null, Locale.ROOT)).isSameAs(formData);
    }

    @Test
    void returnsNewFormDataWhenNoSessionExists() {
        when(sessionRepository.findBy(100L, 200L, null)).thenReturn(Optional.empty());
        when(sessionRepository.save(any(HolonetBotInteractionSession.class))).thenAnswer(invocation -> {
            HolonetBotInteractionSession session = invocation.getArgument(0);
            session.setId(42L);
            return session;
        });
        when(formDataRepository.save(any(HolonetSetupFormData.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var form = service.getOrCreateSetupFormDataSession(100L, 200L, null, Locale.GERMAN);

        assertThat(form.getSession().getId()).isEqualTo(42L);
        assertThat(form.getSession().getGuildId()).isEqualTo(100L);
        assertThat(form.getSession().getMemberId()).isEqualTo(200L);
        assertThat(form.getSession().getChannelId()).isNull();
        assertThat(form.getSession().getLocale()).isEqualTo(Locale.GERMAN);
        verify(sessionRepository).save(form.getSession());
        verify(formDataRepository).save(form);
    }

    @Test
    void returnsNewFormDataWhenTheSessionHasNoFormData() {
        HolonetBotInteractionSession session = new HolonetBotInteractionSession();
        session.setId(42L);
        when(sessionRepository.findBy(100L, 200L, null)).thenReturn(Optional.of(session));
        when(formDataRepository.findBy(42L)).thenReturn(Optional.empty());
        when(formDataRepository.save(any(HolonetSetupFormData.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var form = service.getOrCreateSetupFormDataSession(100L, 200L, null, Locale.ROOT);

        assertThat(form.getSession()).isSameAs(session);
        verify(formDataRepository).save(form);
    }
}
