package de.medo.holonetbridge.commons.provider;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Locale;

import de.medo.holonetbridge.commons.domain.HolonetBotInteractionSession;
import de.medo.holonetbridge.commons.domain.HolonetDiscordConfig;
import de.medo.holonetbridge.commons.domain.HolonetSetupFormData;
import de.medo.holonetbridge.commons.extapi.TicketProvider;
import de.medo.holonetbridge.commons.repository.HolonetDiscordConfigRepository;
import de.medo.holonetbridge.commons.service.HolonetBotInteractionSessionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Import(HolonetBotInteractionSessionService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class HolonetTicketProviderPersistenceTest {
    @Autowired
    private HolonetDiscordConfigRepository discordConfigRepository;
    @Autowired
    private HolonetBotInteractionSessionService botInteractionSessionService;
    @Autowired
    private JdbcTemplate jdbc;
    private final ProviderFactory providerFactory = mock(ProviderFactory.class);

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from holonet_discord_config where guild_id in (10001, 10002, 10003)");
    }

    @Test
    void realCommitFailureLeavesOldDatabaseConfigAndProviderActive() {
        var lifecycle = new HolonetTicketProviderService(discordConfigRepository, botInteractionSessionService, providerFactory);
        var oldProvider = mock(TicketProvider.class);
        var candidate = mock(TicketProvider.class);
        var form = new HolonetSetupFormData();
        var session = new HolonetBotInteractionSession();
        session.setGuildId(10001L);
        session.setMemberId(2L);
        session.setLocale(Locale.ENGLISH);
        form.setSession(session);
        form.setTicketProvider(de.medo.holonetbridge.commons.domain.TicketProvider.GITEA);
        form.setBaseUrl("https://gitea.example.invalid");
        form.setTarget("acme/support");
        form.setToken("encrypted-test-fixture");
        form.setSupportChannelId(4L);
        var success = new HealthCheckResult("ok", true);
        lifecycle.completeSetup(form, success, oldProvider);
        assertThat(discordConfigRepository.findById(10001L)).isPresent();

        // The managed config is flushed at transaction commit, after the service method body returns.
        form.setTarget("a/" + "r".repeat(254));
        assertThatThrownBy(() -> lifecycle.completeSetup(form, success, candidate))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        assertThat(lifecycle.providerFor(10001L)).containsSame(oldProvider);
        assertThat(discordConfigRepository.findById(10001L).orElseThrow().getTarget()).isEqualTo("acme/support");
        verifyNoInteractions(candidate);
    }

    @Test
    void realInvalidEnumRowDoesNotPreventRestoringAnotherGuild() {
        for (long guildId : new long[] {10001, 10002, 10003}) {
            var config = new HolonetDiscordConfig();
            config.setGuildId(guildId);
            config.setTicketProvider(de.medo.holonetbridge.commons.domain.TicketProvider.GITEA);
            config.setBaseUrl("https://gitea.example.invalid");
            config.setTarget("acme/support");
            config.setToken("encrypted-test-fixture");
            discordConfigRepository.saveAndFlush(config);
        }
        jdbc.update("update holonet_discord_config set ticket_provider = 'INVALID' where guild_id = 10002");
        var provider = mock(TicketProvider.class);
        when(providerFactory.create(any(), anyString(), eq("encrypted-test-fixture"), anyString())).thenReturn(provider);
        var lifecycle = new HolonetTicketProviderService(discordConfigRepository, botInteractionSessionService, providerFactory);

        lifecycle.restoreActiveProviders();

        assertThat(lifecycle.providerFor(10001L)).containsSame(provider);
        assertThat(lifecycle.providerFor(10002L)).isEmpty();
        assertThat(lifecycle.providerFor(10003L)).containsSame(provider);
        verifyNoInteractions(provider);
    }
}
