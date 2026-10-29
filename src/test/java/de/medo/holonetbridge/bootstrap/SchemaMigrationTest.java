package de.medo.holonetbridge.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Locale;

import de.medo.holonetbridge.commons.domain.HolonetBotInteractionSession;
import de.medo.holonetbridge.commons.domain.HolonetSetupFormData;
import de.medo.holonetbridge.commons.domain.TicketProvider;
import de.medo.holonetbridge.commons.repository.HolonetBotInteractionSessionRepository;
import de.medo.holonetbridge.commons.repository.HolonetSetupFormDataRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;

class SchemaMigrationTest {

    @TempDir
    Path directory;

    @Test
    void baselineCreatesOnlyCurrentTablesAndSurvivesRestart() {
        Long formId;
        try (var context = start()) {
            var jdbc = context.getBean(JdbcTemplate.class);
            assertThat(jdbc.queryForList(
                            "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'",
                            String.class))
                    .containsExactlyInAnyOrder(
                            "DATABASECHANGELOG",
                            "DATABASECHANGELOGLOCK",
                            "HOLONET_SUPPORT_CHANNEL",
                            "HOLONET_GUILD_SEQUENCE",
                            "HOLONET_BOT_INTERACTION_SESSION",
                            "HOLONET_SETUP_FORM_DATA");
            assertThat(jdbc.queryForList(
                            "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS "
                                    + "WHERE TABLE_NAME = 'HOLONET_SETUP_FORM_DATA'",
                            String.class))
                    .containsExactlyInAnyOrder(
                            "ID", "SESSION_ID", "TICKET_PROVIDER", "BASE_URL", "TARGET", "TOKEN", "SUPPORT_CHANNEL_ID");

            var session = new HolonetBotInteractionSession();
            session.setGuildId(100L);
            session.setMemberId(200L);
            session.setLocale(Locale.GERMAN);
            context.getBean(HolonetBotInteractionSessionRepository.class).saveAndFlush(session);

            var form = new HolonetSetupFormData();
            form.setSession(session);
            form.setTicketProvider(TicketProvider.GITEA);
            form.setTarget("acme/support");
            formId = context.getBean(HolonetSetupFormDataRepository.class).saveAndFlush(form).getId();
        }

        try (var context = start()) {
            var jdbc = context.getBean(JdbcTemplate.class);
            assertThat(jdbc.queryForList("SELECT ID FROM DATABASECHANGELOG", String.class))
                    .containsExactly("1-current-schema");
            var form = context.getBean(HolonetSetupFormDataRepository.class).findById(formId).orElseThrow();
            assertThat(form.getTicketProvider()).isEqualTo(TicketProvider.GITEA);
            assertThat(form.getTarget()).isEqualTo("acme/support");
            assertThat(form.getSession().getLocale()).isEqualTo(Locale.GERMAN);
        }
    }

    private ConfigurableApplicationContext start() {
        return new SpringApplicationBuilder(PersistenceConfiguration.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.config.location=optional:classpath:/schema-migration-test-no-config.yml",
                        "--spring.profiles.active=default",
                        "--spring.datasource.url=jdbc:h2:file:" + directory.resolve("database")
                                + ";DB_CLOSE_ON_EXIT=FALSE",
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=",
                        "--spring.jpa.hibernate.ddl-auto=validate",
                        "--spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.xml");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan("de.medo.holonetbridge")
    @EnableJpaRepositories("de.medo.holonetbridge")
    static class PersistenceConfiguration {}
}
