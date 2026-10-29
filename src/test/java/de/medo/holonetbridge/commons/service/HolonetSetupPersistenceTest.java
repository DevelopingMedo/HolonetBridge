package de.medo.holonetbridge.commons.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.Duration;
import java.util.Locale;

import de.medo.holonetbridge.commons.domain.TicketProvider;
import de.medo.holonetbridge.commons.domain.HolonetSetupFormData;
import de.medo.holonetbridge.commons.repository.HolonetDiscordConfigRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Import(HolonetBotInteractionSessionService.class)
class HolonetSetupPersistenceTest {
    @Autowired
    private HolonetBotInteractionSessionService service;
    @Autowired
    private HolonetDiscordConfigRepository configs;
    @Autowired
    private EntityManager entityManager;

    @Test
    void completedConfigIsSharedAcrossMembersAndWinsOverTheirDrafts() {
        HolonetSetupFormData otherDraft = service.getOrCreateSetupFormDataSession(1, 3, Locale.ENGLISH);
        otherDraft.setBaseUrl("https://unfinished.example");
        service.saveSetupFormDataSession(otherDraft);
        HolonetSetupFormData form = service.getOrCreateSetupFormDataSession(1, 2, Locale.GERMAN);
        form.setTicketProvider(TicketProvider.GITEA);
        form.setBaseUrl("https://saved.example");
        form.setTarget("acme/support");
        form.setToken("encrypted-token");
        form.setSupportChannelId(4L);
        service.saveSetupFormDataSession(form);
        service.completeSetup(form);
        entityManager.flush();
        entityManager.clear();

        HolonetSetupFormData opened = service.getOrCreateSetupFormDataSession(1, 3, Locale.GERMAN);
        assertThat(opened.getBaseUrl()).isEqualTo("https://saved.example");
        assertThat(opened.getToken()).isEqualTo("encrypted-token");
        assertThat(opened.getId()).isNull();
        assertThat(opened.getSession().getId()).isNull();
        assertThat(service.getOrCreateSetupFormDataSession(9, 3, Locale.GERMAN).getBaseUrl()).isNull();
        opened.setBaseUrl("https://failed-check.example");
        service.saveSetupFormDataSession(opened);
        entityManager.flush();
        entityManager.clear();
        assertThat(configs.findById(1L).orElseThrow().getBaseUrl()).isEqualTo("https://saved.example");
        assertThat(service.getOrCreateSetupFormDataSession(1, 3, Locale.GERMAN).getBaseUrl()).isEqualTo("https://saved.example");
        service.completeSetup(opened);
        entityManager.flush();
        entityManager.clear();
        assertThat(configs.findById(1L).orElseThrow().getBaseUrl()).isEqualTo("https://failed-check.example");
    }

    @Test
    void draftSurvivesReopeningButExpiresAfterOneHourWithoutActivity() {
        HolonetSetupFormData form = service.getOrCreateSetupFormDataSession(1, 2, Locale.GERMAN);
        form.setBaseUrl("https://unfinished.example");
        service.saveSetupFormDataSession(form);
        entityManager.flush();
        Long oldSessionId = form.getSession().getId();
        entityManager.clear();
        assertThat(service.getOrCreateSetupFormDataSession(1, 2, Locale.ENGLISH).getBaseUrl()).isEqualTo("https://unfinished.example");
        assertThat(service.getOrCreateSetupFormDataSession(1, 3, Locale.ENGLISH).getBaseUrl()).isNull();
        entityManager.flush();
        entityManager.createNativeQuery("update holonet_bot_interaction_session set updated_at = :old where id = :id")
                .setParameter("old", Instant.now().minus(Duration.ofHours(2)))
                .setParameter("id", oldSessionId).executeUpdate();
        entityManager.clear();

        HolonetSetupFormData fresh = service.getOrCreateSetupFormDataSession(1, 2, Locale.GERMAN);
        assertThat(fresh.getBaseUrl()).isNull();
        assertThat(fresh.getSession().getId()).isNotEqualTo(oldSessionId);
        assertThat(fresh.getSession().getChannelId()).isNull();
    }
}
