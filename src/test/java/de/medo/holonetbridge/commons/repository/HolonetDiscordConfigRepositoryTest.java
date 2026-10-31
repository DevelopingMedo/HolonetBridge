package de.medo.holonetbridge.commons.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import de.medo.holonetbridge.commons.domain.HolonetDiscordConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class HolonetDiscordConfigRepositoryTest {
    @Autowired
    private HolonetDiscordConfigRepository repository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void insertAndRealUpdateMaintainTimestampsButUnchangedSaveDoesNot() {
        Instant beforeInsert = Instant.now();
        var config = new HolonetDiscordConfig();
        config.setGuildId(700L);
        config.setTarget("before");
        config = repository.saveAndFlush(config);
        assertThat(config.getCreatedAt()).isEqualTo(config.getUpdatedAt()).isAfterOrEqualTo(beforeInsert);
        entityManager.refresh(config);
        Instant createdAt = config.getCreatedAt();
        Instant initialUpdatedAt = config.getUpdatedAt();

        repository.saveAndFlush(config);
        entityManager.refresh(config);
        assertThat(config.getCreatedAt()).isEqualTo(createdAt);
        assertThat(config.getUpdatedAt()).isEqualTo(initialUpdatedAt);

        config.setTarget("after");
        config.setNextTicketChannelSuffixNumber(repository.nextTicketChannelSuffixNumber());
        repository.saveAndFlush(config);
        entityManager.clear();
        var reloaded = repository.findById(700L).orElseThrow();
        assertThat(reloaded.getCreatedAt()).isEqualTo(createdAt);
        assertThat(reloaded.getUpdatedAt()).isAfter(initialUpdatedAt);
        assertThat(reloaded.getTarget()).isEqualTo("after");
        assertThat(reloaded.getNextTicketChannelSuffixNumber()).isEqualTo(config.getNextTicketChannelSuffixNumber());
    }

    @Test
    void parallelAttemptsReserveDistinctGlobalNumbersWithoutSavingAnyConfig() throws Exception {
        var barrier = new CyclicBarrier(2);
        Callable<Long> attempt = () -> {
            barrier.await(5, TimeUnit.SECONDS);
            return repository.nextTicketChannelSuffixNumber();
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(attempt);
            var second = executor.submit(attempt);
            long firstNumber = first.get(10, TimeUnit.SECONDS);
            long secondNumber = second.get(10, TimeUnit.SECONDS);
            assertThat(firstNumber).isPositive().isNotEqualTo(secondNumber);
            assertThat(secondNumber).isPositive();
            assertThat(repository.nextTicketChannelSuffixNumber()).isGreaterThan(Math.max(firstNumber, secondNumber));
            assertThat(repository.count()).isZero();
        }
    }
}
