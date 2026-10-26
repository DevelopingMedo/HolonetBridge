package de.medo.holonetbridge.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import de.medo.holonetbridge.HolonetBridgeApplication;
import de.medo.holonetbridge.commons.discord.setup.HolonetDiscordServerRepository;
import de.medo.holonetbridge.commons.discord.ticketing.HolonetSupportChannelRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import net.dv8tion.jda.api.JDA;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Drives the Liquibase/Hibernate-validate migration setup (see {@code application.yml} and
 * {@code db/changelog}) against the two scenarios it has to handle in production: a brand-new database file
 * and a database file that already has the baseline schema from before Liquibase existed in this project
 * (see {@code fixtures/legacy-h2/README.md}). {@code ddl-auto: update} alone never exercised either case;
 * both are full Spring context startups, since that is where schema validation actually happens.
 */
class MigrationSmokeTest {

    @Nested
    @SpringBootTest(classes = HolonetBridgeApplication.class, properties = {
        "holonet.provider.id=gitea",
        "holonet.provider.base-url=https://tickets.example.invalid",
        "holonet.provider.token=test-token",
        "holonet.provider.gitea.owner=acme",
        "holonet.provider.gitea.repository=support",
        "holonet.discord.token=test-discord-token"
    })
    class WithABrandNewDatabase {

        @TempDir
        static Path tempDir;

        @DynamicPropertySource
        static void freshDatabaseUrl(DynamicPropertyRegistry registry) {
            registry.add("spring.datasource.url", () -> "jdbc:h2:file:" + tempDir.resolve("fresh-holonet-bridge"));
        }

        @MockitoBean
        private JDA jda;

        @Autowired
        private HolonetDiscordServerRepository discordServerRepository;

        @Test
        void liquibaseCreatesTheSchemaFromScratchAndHibernateValidatesAgainstIt() {
            // The context starting up at all is the main assertion: ddl-auto=validate would have failed
            // startup if Liquibase had not created a schema matching the entities. findAll() additionally
            // confirms the repository can actually be queried against that freshly created schema.
            assertThat(discordServerRepository.findAll()).isEmpty();
        }
    }

    @Nested
    @SpringBootTest(classes = HolonetBridgeApplication.class, properties = {
        "holonet.provider.id=gitea",
        "holonet.provider.base-url=https://tickets.example.invalid",
        "holonet.provider.token=test-token",
        "holonet.provider.gitea.owner=acme",
        "holonet.provider.gitea.repository=support",
        "holonet.discord.token=test-discord-token"
    })
    class WithAPreExistingLegacyDatabase {

        @TempDir
        static Path tempDir;

        @DynamicPropertySource
        static void legacyDatabaseUrl(DynamicPropertyRegistry registry) throws IOException {
            copyLegacyFixtureInto(tempDir);
            registry.add("spring.datasource.url", () -> "jdbc:h2:file:" + tempDir.resolve("legacy-holonet-bridge"));
        }

        @MockitoBean
        private JDA jda;

        @Autowired
        private HolonetDiscordServerRepository discordServerRepository;

        @Autowired
        private HolonetSupportChannelRepository supportChannelRepository;

        @Test
        void liquibaseRecognizesTheExistingBaselineSchemaWithoutRecreatingItOrLosingData() {
            // The context starting up at all shows that Liquibase's "mark ran" preConditions correctly
            // treated the already-present tables as the baseline, instead of failing on (or destructively
            // replacing) a CREATE TABLE against tables that already existed from the pre-Liquibase install.
            assertThat(discordServerRepository.findById(1L)).hasValueSatisfying(server -> {
                assertThat(server.channelId()).isEqualTo(111L);
                assertThat(server.messageId()).isEqualTo(222L);
            });
            assertThat(supportChannelRepository.findById(333L)).hasValueSatisfying(channel -> {
                assertThat(channel.guildId()).isEqualTo(1L);
                assertThat(channel.number()).isEqualTo(1L);
                assertThat(channel.requesterUserId()).isEqualTo(444L);
                assertThat(channel.ticketId()).isEqualTo("LEGACY-1");
            });
        }
    }

    private static void copyLegacyFixtureInto(Path tempDir) throws IOException {
        try (InputStream fixture = MigrationSmokeTest.class.getClassLoader()
                .getResourceAsStream("fixtures/legacy-h2/legacy-holonet-bridge.mv.db")) {
            if (fixture == null) {
                throw new IOException("Legacy H2 fixture not found on the test classpath");
            }
            Files.copy(fixture, tempDir.resolve("legacy-holonet-bridge.mv.db"));
        }
    }
}
