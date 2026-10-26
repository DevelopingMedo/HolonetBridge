package de.medo.holonetbridge.commons.discord.ticketing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

/**
 * Verifies the JPA mapping of {@link HolonetGuildSequence} and the atomic {@code increment} update against a
 * real (in-memory, test-scoped) database. The actual concurrency guarantee of the full
 * {@code HolonetGuildSequenceService} (which needs real {@code @Transactional} proxying, unavailable under
 * plain {@code @DataJpaTest}) is covered separately by {@code HolonetGuildSequenceServiceConcurrencyTest}.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class HolonetGuildSequenceRepositoryTest {

    @Autowired
    private HolonetGuildSequenceRepository repository;

    @Test
    void savedSequenceCanBeLoadedBackByGuildId() {
        repository.save(new HolonetGuildSequence(999L, 0L));

        assertThat(repository.findById(999L)).hasValueSatisfying(
                sequence -> assertThat(sequence.currentNumber()).isEqualTo(0L));
    }

    @Test
    void incrementAtomicallyBumpsCurrentNumberAndReturnsTheNumberOfUpdatedRows() {
        repository.save(new HolonetGuildSequence(999L, 0L));

        int updated = repository.increment(999L);

        assertThat(updated).isEqualTo(1);
        assertThat(repository.findById(999L)).hasValueSatisfying(
                sequence -> assertThat(sequence.currentNumber()).isEqualTo(1L));
    }

    @Test
    void incrementReturnsZeroForAGuildWithoutARowInsteadOfCreatingOne() {
        int updated = repository.increment(999L);

        assertThat(updated).isEqualTo(0);
        assertThat(repository.findById(999L)).isEmpty();
    }

    @Test
    void lockForUpdateReturnsTheExistingRowOrEmptyForAnUnknownGuild() {
        repository.save(new HolonetGuildSequence(999L, 3L));

        assertThat(repository.lockForUpdate(999L)).hasValueSatisfying(
                sequence -> assertThat(sequence.currentNumber()).isEqualTo(3L));
        assertThat(repository.lockForUpdate(888L)).isEmpty();
    }

    @Test
    void twoGuildsEachHaveTheirOwnIndependentCounter() {
        repository.save(new HolonetGuildSequence(999L, 5L));
        repository.save(new HolonetGuildSequence(888L, 0L));

        repository.increment(999L);

        assertThat(repository.findById(999L)).hasValueSatisfying(
                sequence -> assertThat(sequence.currentNumber()).isEqualTo(6L));
        assertThat(repository.findById(888L)).hasValueSatisfying(
                sequence -> assertThat(sequence.currentNumber()).isEqualTo(0L));
    }
}
