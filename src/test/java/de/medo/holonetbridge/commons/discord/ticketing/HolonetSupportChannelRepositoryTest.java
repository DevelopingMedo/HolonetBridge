package de.medo.holonetbridge.commons.discord.ticketing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

/**
 * Verifies the JPA mapping of {@link HolonetSupportChannel} and the {@code findTopByGuildIdOrderByNumberDesc}
 * lookup used to generate the next support-channel name, against a real (in-memory, test-scoped) database.
 */
@DataJpaTest
class HolonetSupportChannelRepositoryTest {

    @Autowired
    private HolonetSupportChannelRepository repository;

    @Test
    void savedChannelCanBeLoadedBackByChannelId() {
        repository.save(new HolonetSupportChannel(111L, 999L, 123L, 1L));

        assertThat(repository.findById(111L)).hasValueSatisfying(channel -> {
            assertThat(channel.channelId()).isEqualTo(111L);
            assertThat(channel.guildId()).isEqualTo(999L);
            assertThat(channel.requesterUserId()).isEqualTo(123L);
            assertThat(channel.number()).isEqualTo(1L);
        });
    }

    @Test
    void findTopByGuildIdOrderByNumberDescReturnsTheHighestNumberForThatGuild() {
        repository.save(new HolonetSupportChannel(111L, 999L, 123L, 1L));
        repository.save(new HolonetSupportChannel(222L, 999L, 124L, 3L));
        repository.save(new HolonetSupportChannel(333L, 999L, 125L, 2L));
        repository.save(new HolonetSupportChannel(444L, 888L, 126L, 10L));

        assertThat(repository.findTopByGuildIdOrderByNumberDesc(999L))
                .hasValueSatisfying(channel -> assertThat(channel.number()).isEqualTo(3L));
    }

    @Test
    void ticketIdCanBeSetAfterCreation() {
        HolonetSupportChannel channel = new HolonetSupportChannel(111L, 999L, 123L, 1L);
        channel.ticketId("42");
        repository.save(channel);

        assertThat(repository.findById(111L)).hasValueSatisfying(
                saved -> assertThat(saved.ticketId()).isEqualTo("42"));
    }
}
