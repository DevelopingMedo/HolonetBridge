package de.medo.holonetbridge.commons.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Locale;
import java.util.Optional;

import de.medo.holonetbridge.commons.domain.HolonetBotInteractionSession;
import de.medo.holonetbridge.commons.domain.HolonetSetupFormData;
import de.medo.holonetbridge.commons.repository.HolonetBotInteractionSessionRepository;
import de.medo.holonetbridge.commons.repository.HolonetSetupFormDataRepository;
import org.junit.jupiter.api.Test;

class HolonetBotInteractionSessionServiceTest {

    private final HolonetBotInteractionSessionRepository sessionRepository =
            mock(HolonetBotInteractionSessionRepository.class);
    private final HolonetSetupFormDataRepository formDataRepository = mock(HolonetSetupFormDataRepository.class);
    private final HolonetBotInteractionSessionService service =
            new HolonetBotInteractionSessionService(sessionRepository, formDataRepository);

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

        assertThat(service.getOrCreateSetupFormDataSession(100L, 200L, null, Locale.ROOT))
                .usingRecursiveComparison()
                .isEqualTo(new HolonetSetupFormData());
        verifyNoInteractions(formDataRepository);
    }

    @Test
    void returnsNewFormDataWhenTheSessionHasNoFormData() {
        HolonetBotInteractionSession session = new HolonetBotInteractionSession();
        session.setId(42L);
        when(sessionRepository.findBy(100L, 200L, null)).thenReturn(Optional.of(session));
        when(formDataRepository.findBy(42L)).thenReturn(Optional.empty());

        assertThat(service.getOrCreateSetupFormDataSession(100L, 200L, null, Locale.ROOT))
                .usingRecursiveComparison()
                .isEqualTo(new HolonetSetupFormData());
    }
}
