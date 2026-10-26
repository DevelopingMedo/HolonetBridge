package de.medo.holonetbridge.commons.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import de.medo.holonetbridge.commons.crypto.EncryptedSecret;
import de.medo.holonetbridge.commons.crypto.TokenCryptoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

/**
 * Verifies the {@code holonet_provider_connection} JPA mapping and - through a manually constructed
 * {@link HolonetProviderConnectionService}, the sole writer of this table - the "exactly one active
 * connection per guild" invariant against a real (in-memory, test-scoped) database: a target change must
 * create a new row while leaving the old one deactivated but not deleted, and a token-only change on the
 * same target must update the existing row in place instead of creating a new one.
 *
 * <p>{@code @DataJpaTest} does not run Liquibase, so {@code ddl-auto} is overridden back to
 * {@code create-drop} here instead of inheriting the application-wide {@code validate}; the migration
 * itself is covered separately by the full-context migration smoke test.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class HolonetProviderConnectionRepositoryTest {

    private static final long GUILD_ID = 100L;
    private static final String BASE_URL = "https://gitea.example.invalid";
    private static final String OWNER = "acme";
    private static final String REPOSITORY = "support";

    @Autowired
    private HolonetProviderConnectionRepository repository;

    private final TokenCryptoService tokenCryptoService = mock(TokenCryptoService.class);
    private HolonetProviderConnectionService service;

    @BeforeEach
    void setUp() {
        when(tokenCryptoService.encrypt(any(), any()))
                .thenReturn(new EncryptedSecret(new byte[] {1, 2, 3}, new byte[] {4, 5, 6}, "v1"));
        service = new HolonetProviderConnectionService(
                repository, tokenCryptoService, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    }

    @Test
    void activatingAFreshGuildCreatesExactlyOneActiveRow() {
        service.activate(GUILD_ID, HolonetProviderType.GITEA, BASE_URL, OWNER, REPOSITORY, "token");

        List<HolonetProviderConnection> all = repository.findAllByGuildId(GUILD_ID);
        assertThat(all).hasSize(1);
        assertThat(all.get(0).active()).isTrue();
        assertThat(repository.findByGuildIdAndActiveTrue(GUILD_ID)).isPresent();
    }

    @Test
    void changingTheTargetDeactivatesTheOldRowWithoutDeletingItAndCreatesANewActiveOne() {
        service.activate(GUILD_ID, HolonetProviderType.GITEA, BASE_URL, OWNER, REPOSITORY, "token");
        UUID firstId = repository.findByGuildIdAndActiveTrue(GUILD_ID).orElseThrow().id();

        service.activate(GUILD_ID, HolonetProviderType.GITEA, BASE_URL, OWNER, "a-different-repository", "new-token");

        List<HolonetProviderConnection> all = repository.findAllByGuildId(GUILD_ID);
        assertThat(all).hasSize(2);
        assertThat(all).filteredOn(connection -> connection.id().equals(firstId)).hasSize(1).allSatisfy(connection -> {
            assertThat(connection.active()).isFalse();
            assertThat(connection.repository()).isEqualTo(REPOSITORY);
        });

        List<HolonetProviderConnection> activeRows =
                all.stream().filter(HolonetProviderConnection::active).toList();
        assertThat(activeRows).hasSize(1);
        assertThat(activeRows.get(0).id()).isNotEqualTo(firstId);
        assertThat(activeRows.get(0).repository()).isEqualTo("a-different-repository");
    }

    @Test
    void aTokenOnlyChangeOnTheSameTargetUpdatesTheExistingRowInPlaceInsteadOfCreatingANewOne() {
        service.activate(GUILD_ID, HolonetProviderType.GITEA, BASE_URL, OWNER, REPOSITORY, "token");
        UUID firstId = repository.findByGuildIdAndActiveTrue(GUILD_ID).orElseThrow().id();

        service.activate(GUILD_ID, HolonetProviderType.GITEA, BASE_URL, OWNER, REPOSITORY, "rotated-token");

        List<HolonetProviderConnection> all = repository.findAllByGuildId(GUILD_ID);
        assertThat(all).hasSize(1);
        assertThat(all.get(0).id()).isEqualTo(firstId);
        assertThat(all.get(0).active()).isTrue();
    }

    @Test
    void aBlankTokenKeepOnTheSameTargetLeavesTheExistingRowAsTheOnlyOneForTheGuild() {
        service.activate(GUILD_ID, HolonetProviderType.GITEA, BASE_URL, OWNER, REPOSITORY, "token");
        UUID firstId = repository.findByGuildIdAndActiveTrue(GUILD_ID).orElseThrow().id();

        service.activate(GUILD_ID, HolonetProviderType.GITEA, BASE_URL, OWNER, REPOSITORY, null);

        List<HolonetProviderConnection> all = repository.findAllByGuildId(GUILD_ID);
        assertThat(all).hasSize(1);
        assertThat(all.get(0).id()).isEqualTo(firstId);
    }

    @Test
    void changingTheTargetWithoutATokenIsRejectedAndLeavesTheExistingActiveConnectionUntouched() {
        service.activate(GUILD_ID, HolonetProviderType.GITEA, BASE_URL, OWNER, REPOSITORY, "token");
        UUID firstId = repository.findByGuildIdAndActiveTrue(GUILD_ID).orElseThrow().id();

        assertThatThrownBy(() -> service.activate(
                        GUILD_ID, HolonetProviderType.GITEA, BASE_URL, OWNER, "a-different-repository", null))
                .isInstanceOf(IllegalArgumentException.class);

        List<HolonetProviderConnection> all = repository.findAllByGuildId(GUILD_ID);
        assertThat(all).hasSize(1);
        assertThat(all.get(0).id()).isEqualTo(firstId);
        assertThat(all.get(0).active()).isTrue();
        assertThat(all.get(0).repository()).isEqualTo(REPOSITORY);
    }

    @Test
    void twoGuildsEachHaveTheirOwnIndependentActiveConnection() {
        long otherGuildId = 200L;
        service.activate(GUILD_ID, HolonetProviderType.GITEA, BASE_URL, OWNER, REPOSITORY, "token-a");
        service.activate(otherGuildId, HolonetProviderType.GITEA, BASE_URL, OWNER, "other-repository", "token-b");

        assertThat(repository.findByGuildIdAndActiveTrue(GUILD_ID)).hasValueSatisfying(
                connection -> assertThat(connection.repository()).isEqualTo(REPOSITORY));
        assertThat(repository.findByGuildIdAndActiveTrue(otherGuildId)).hasValueSatisfying(
                connection -> assertThat(connection.repository()).isEqualTo("other-repository"));
    }
}
