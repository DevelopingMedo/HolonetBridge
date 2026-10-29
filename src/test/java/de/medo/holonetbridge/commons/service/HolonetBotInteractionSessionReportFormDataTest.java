package de.medo.holonetbridge.commons.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Locale;

import de.medo.holonetbridge.commons.domain.ticket.HolonetTicketKind;
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
        var bug = service.getOrCreate(1, 2, 3, Locale.GERMAN, HolonetTicketKind.BUG);
        bug.setTitle("  Original title  ");
        bug.setSteps("s".repeat(4000));
        bug.setExpected("Expected");
        bug.setActual("Actual\nwith newline");
        bug.setRecurrence("sometimes");
        service.save(bug);
        var feature = service.getOrCreate(1, 2, 3, Locale.ENGLISH, HolonetTicketKind.FEATURE);
        feature.setTitle("Idea");
        feature.setDescription("d".repeat(4000));
        service.save(feature);
        entityManager.clear();

        var restored = service.getOrCreate(1, 2, 3, Locale.ENGLISH, HolonetTicketKind.BUG);
        assertThat(restored.getId()).isEqualTo(bug.getId());
        assertThat(restored.getTitle()).isEqualTo(bug.getTitle());
        assertThat(restored.getSteps()).isEqualTo(bug.getSteps());
        assertThat(restored.getExpected()).isEqualTo(bug.getExpected());
        assertThat(restored.getActual()).isEqualTo(bug.getActual());
        assertThat(restored.getRecurrence()).isEqualTo(bug.getRecurrence());
        var restoredFeature = service.getOrCreate(1, 2, 3, Locale.ENGLISH, HolonetTicketKind.FEATURE);
        assertThat(restoredFeature.getDescription()).hasSize(4000);
        assertThat(restoredFeature.getTitle()).isEqualTo("Idea");
        assertThat(restoredFeature.getSession().getId()).isEqualTo(restored.getSession().getId());
    }

    @Test
    void isolatesGuildMemberAndChannelAndDoesNotResumeClosedSessions() {
        var original = service.getOrCreate(1, 2, 3, Locale.ENGLISH, HolonetTicketKind.BUG);
        original.setTitle("Private draft");
        service.save(original);
        assertThat(service.getOrCreate(9, 2, 3, Locale.ENGLISH, HolonetTicketKind.BUG).getTitle()).isNull();
        assertThat(service.getOrCreate(1, 9, 3, Locale.ENGLISH, HolonetTicketKind.BUG).getTitle()).isNull();
        assertThat(service.getOrCreate(1, 2, 9, Locale.ENGLISH, HolonetTicketKind.BUG).getTitle()).isNull();

        original.getSession().setClosedAt(Instant.now());
        entityManager.flush();
        entityManager.clear();
        var reopened = service.getOrCreate(1, 2, 3, Locale.ENGLISH, HolonetTicketKind.BUG);
        assertThat(reopened.getTitle()).isNull();
        assertThat(reopened.getSession().getId()).isNotEqualTo(original.getSession().getId());
    }
}
