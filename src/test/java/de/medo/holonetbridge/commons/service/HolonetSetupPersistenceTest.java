package de.medo.holonetbridge.commons.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.Duration;
import java.util.Locale;

import de.medo.holonetbridge.commons.domain.TicketProvider;
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
        var otherDraft = service.openSetup(1, 3, Locale.ENGLISH).formData();
        otherDraft.setBaseUrl("https://unfinished.example");
        service.saveSetupFormDataSession(otherDraft);
        var form = service.openSetup(1, 2, Locale.GERMAN).formData();
        form.setTicketProvider(TicketProvider.GITEA);
        form.setBaseUrl("https://saved.example");
        form.setTarget("acme/support");
        form.setToken("encrypted-token");
        form.setSupportChannelId(4L);
        service.saveSetupFormDataSession(form);
        service.completeSetup(form);
        entityManager.flush();
        entityManager.clear();

        var opened = service.openSetup(1, 3, Locale.GERMAN).formData();
        assertThat(opened.getBaseUrl()).isEqualTo("https://saved.example");
        assertThat(opened.getToken()).isEqualTo("encrypted-token");
        assertThat(opened.getSession()).isNull();
        assertThat(service.openSetup(9, 3, Locale.GERMAN).formData().getBaseUrl()).isNull();
        opened.setBaseUrl("https://failed-check.example");
        var submitted = service.getOrCreateSetupFormDataSession(1, 3, Locale.GERMAN);
        submitted.setBaseUrl(opened.getBaseUrl());
        service.saveSetupFormDataSession(submitted);
        entityManager.flush();
        entityManager.clear();
        assertThat(configs.findById(1L).orElseThrow().getBaseUrl()).isEqualTo("https://saved.example");
        assertThat(service.openSetup(1, 3, Locale.GERMAN).formData().getBaseUrl()).isEqualTo("https://saved.example");
    }

    @Test
    void draftSurvivesReopeningButExpiresAfterOneHourWithoutActivity() {
        var form = service.openSetup(1, 2, Locale.GERMAN).formData();
        form.setBaseUrl("https://unfinished.example");
        service.saveSetupFormDataSession(form);
        entityManager.flush();
        var oldSessionId = form.getSession().getId();
        entityManager.clear();
        assertThat(service.openSetup(1, 2, Locale.ENGLISH).formData().getBaseUrl()).isEqualTo("https://unfinished.example");
        assertThat(service.openSetup(1, 3, Locale.ENGLISH).formData().getBaseUrl()).isNull();
        entityManager.flush();
        entityManager.createNativeQuery("update holonet_bot_interaction_session set updated_at = :old where id = :id")
                .setParameter("old", Instant.now().minus(Duration.ofHours(2)))
                .setParameter("id", oldSessionId).executeUpdate();
        entityManager.clear();

        var fresh = service.openSetup(1, 2, Locale.GERMAN).formData();
        assertThat(fresh.getBaseUrl()).isNull();
        assertThat(fresh.getSession().getId()).isNotEqualTo(oldSessionId);
        assertThat(fresh.getSession().getChannelId()).isNull();
    }
}
