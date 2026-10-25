package de.medo.holonetbridge.commons.discord;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

/**
 * Verifies the JPA mapping against a real (in-memory, test-scoped) database rather than mocking the
 * repository, since what actually needs checking here is the mapping itself: that {@code guildId} is the
 * primary key, so re-saving a configuration for the same guild overwrites rather than duplicates it.
 */
@DataJpaTest
class GuildTicketChannelConfigurationRepositoryTest {

    @Autowired
    private GuildTicketChannelConfigurationRepository repository;

    @Test
    void savedConfigurationCanBeLoadedBackByGuildId() {
        repository.save(new GuildTicketChannelConfiguration(1L, 2L, 3L));

        assertThat(repository.findById(1L)).hasValueSatisfying(configuration -> {
            assertThat(configuration.guildId()).isEqualTo(1L);
            assertThat(configuration.channelId()).isEqualTo(2L);
            assertThat(configuration.messageId()).isEqualTo(3L);
        });
    }

    @Test
    void reconfiguringTheSameGuildOverwritesThePreviousChannelAndMessageInstead() {
        repository.save(new GuildTicketChannelConfiguration(1L, 2L, 3L));
        repository.save(new GuildTicketChannelConfiguration(1L, 20L, 30L));

        assertThat(repository.findAll()).hasSize(1);
        assertThat(repository.findById(1L)).hasValueSatisfying(configuration -> {
            assertThat(configuration.channelId()).isEqualTo(20L);
            assertThat(configuration.messageId()).isEqualTo(30L);
        });
    }
}
