package de.medo.holonetbridge.commons.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Locale;

import de.medo.holonetbridge.commons.domain.ticket.HolonetTicketKind;
import de.medo.holonetbridge.commons.domain.HolonetBotInteractionSession;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import(HolonetBotInteractionSessionService.class)
class HolonetBotInteractionSessionReportFormDataTest {
    @Autowired
    private HolonetBotInteractionSessionService service;
    @Autowired
    private EntityManager entityManager;

    @Test
    void restoresAllFieldsAfterPersistenceContextIsClearedAndKeepsKindsSeparate() {
        var bug = service.getOrCreate(1, 2, Locale.GERMAN, HolonetTicketKind.BUG);
        bug.setTitle("  Original title  ");
        bug.setSteps("s".repeat(4000));
        bug.setExpected("Expected");
        bug.setActual("Actual\nwith newline");
        bug.setRecurrence("sometimes");
        service.save(bug);
        var feature = service.getOrCreate(1, 2, Locale.ENGLISH, HolonetTicketKind.FEATURE);
        feature.setTitle("Idea");
        feature.setDescription("d".repeat(4000));
        service.save(feature);
        entityManager.clear();

        var restored = service.getOrCreate(1, 2, Locale.ENGLISH, HolonetTicketKind.BUG);
        assertThat(restored.getId()).isEqualTo(bug.getId());
        assertThat(restored.getTitle()).isEqualTo(bug.getTitle());
        assertThat(restored.getSteps()).isEqualTo(bug.getSteps());
        assertThat(restored.getExpected()).isEqualTo(bug.getExpected());
        assertThat(restored.getActual()).isEqualTo(bug.getActual());
        assertThat(restored.getRecurrence()).isEqualTo(bug.getRecurrence());
        var restoredFeature = service.getOrCreate(1, 2, Locale.ENGLISH, HolonetTicketKind.FEATURE);
        assertThat(restoredFeature.getDescription()).hasSize(4000);
        assertThat(restoredFeature.getTitle()).isEqualTo("Idea");
        assertThat(restoredFeature.getSession().getId()).isEqualTo(restored.getSession().getId());
    }

    @Test
    void resumesDraftByGuildAndMemberAndDoesNotResumeClosedSessions() {
        var original = service.getOrCreate(1, 2, Locale.of("hr"), HolonetTicketKind.BUG);
        original.setTitle("Private draft");
        original.setSteps("Saved steps\n**unchanged**");
        service.save(original);
        assertThat(service.getOrCreate(9, 2, Locale.ENGLISH, HolonetTicketKind.BUG).getTitle()).isNull();
        assertThat(service.getOrCreate(1, 9, Locale.ENGLISH, HolonetTicketKind.BUG).getTitle()).isNull();
        entityManager.flush();
        entityManager.clear();
        var resumed = service.getOrCreate(1, 2, Locale.GERMAN, HolonetTicketKind.BUG);
        assertThat(resumed.getId()).isEqualTo(original.getId());
        assertThat(resumed.getSession().getId()).isEqualTo(original.getSession().getId());
        assertThat(resumed.getTitle()).isEqualTo("Private draft");
        assertThat(resumed.getSteps()).isEqualTo("Saved steps\n**unchanged**");
        assertThat(resumed.getSession().getLocale()).isEqualTo(Locale.GERMAN);
        entityManager.flush();
        entityManager.clear();
        resumed = entityManager.find(de.medo.holonetbridge.commons.domain.HolonetReportFormData.class, original.getId());
        assertThat(resumed.getSession().getId()).isEqualTo(original.getSession().getId());
        assertThat(resumed.getSession().getLocale()).isEqualTo(Locale.GERMAN);
        assertThat(resumed.getTitle()).isEqualTo("Private draft");
        assertThat(resumed.getSteps()).isEqualTo("Saved steps\n**unchanged**");

        resumed.getSession().setClosedAt(Instant.now());
        entityManager.flush();
        entityManager.clear();
        var reopened = service.getOrCreate(1, 2, Locale.ENGLISH, HolonetTicketKind.BUG);
        assertThat(reopened.getTitle()).isNull();
        assertThat(reopened.getSession().getId()).isNotEqualTo(original.getSession().getId());
    }

    @Test
    void selectsLatestUpdatedOpenSessionAndBreaksTimestampTiesById() {
        var first = persistedSession();
        var second = persistedSession();
        var closed = persistedSession();
        closed.setClosedAt(Instant.now());
        entityManager.flush();
        Instant latest = Instant.parse("2026-01-02T00:00:00Z");
        setUpdatedAt(first.getId(), latest);
        setUpdatedAt(second.getId(), latest.minusSeconds(60));
        setUpdatedAt(closed.getId(), latest.plusSeconds(60));
        entityManager.clear();

        assertThat(service.getOrCreateBotInteractionSession(1, 2, Locale.ENGLISH).getId())
                .isEqualTo(first.getId());

        setUpdatedAt(second.getId(), latest);
        entityManager.clear();

        assertThat(second.getId()).isGreaterThan(first.getId());
        assertThat(service.getOrCreateBotInteractionSession(1, 2, Locale.ENGLISH).getId())
                .isEqualTo(second.getId());
    }

    private HolonetBotInteractionSession persistedSession() {
        var session = new HolonetBotInteractionSession();
        session.setGuildId(1L);
        session.setMemberId(2L);
        session.setLocale(Locale.ENGLISH);
        entityManager.persist(session);
        return session;
    }

    private void setUpdatedAt(Long id, Instant updatedAt) {
        entityManager.createNativeQuery("update holonet_bot_interaction_session set updated_at = :updatedAt where id = :id")
                .setParameter("updatedAt", updatedAt)
                .setParameter("id", id)
                .executeUpdate();
    }
}
