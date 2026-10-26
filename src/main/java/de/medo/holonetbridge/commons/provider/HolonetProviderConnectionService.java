package de.medo.holonetbridge.commons.provider;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import de.medo.holonetbridge.commons.crypto.EncryptedSecret;
import de.medo.holonetbridge.commons.crypto.TokenCryptoService;
import de.medo.holonetbridge.commons.crypto.TokenEncryptionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns every write path to {@link HolonetProviderConnection}, so the "exactly one active connection per
 * guild" invariant and the new-row-vs-update-in-place decision below are enforced in exactly one place
 * instead of being re-derived (and potentially gotten wrong) at every call site.
 *
 * <p>Callers (the {@code /setup} flow) must run every validation - permissions, channel access, URL syntax,
 * SSRF policy, and an actual read against the target repository - to completion <em>before</em> calling
 * {@link #activate}: this method does not validate reachability itself, so a successful call always takes
 * effect, and a failed one (an exception from here) always leaves the previous active connection, if any,
 * completely untouched because of {@link Transactional @Transactional}.
 */
@Service
public class HolonetProviderConnectionService {

    private final HolonetProviderConnectionRepository repository;
    private final TokenCryptoService tokenCryptoService;
    private final Clock clock;

    /**
     * Explicitly {@code @Autowired} because this class also declares a package-private, {@code Clock}-taking
     * constructor for tests; without the annotation Spring cannot pick a constructor between the two and
     * falls back to a (nonexistent) no-arg one instead of this public one.
     */
    @Autowired
    public HolonetProviderConnectionService(
            HolonetProviderConnectionRepository repository, TokenCryptoService tokenCryptoService) {
        this(repository, tokenCryptoService, Clock.systemUTC());
    }

    HolonetProviderConnectionService(
            HolonetProviderConnectionRepository repository, TokenCryptoService tokenCryptoService, Clock clock) {
        this.repository = repository;
        this.tokenCryptoService = tokenCryptoService;
        this.clock = clock;
    }

    public Optional<HolonetProviderConnection> activeConnection(long guildId) {
        return repository.findByGuildIdAndActiveTrue(guildId);
    }

    /**
     * Activates a connection for {@code guildId} to the given target, with the credential-handling rules the
     * {@code /setup} modal exposes to the user:
     *
     * <ul>
     *   <li>same target (base URL, owner, repository all unchanged) as the current active connection, and
     *       {@code plaintextToken} is {@code null} ("keep"): the existing row is left as is (still saved, to
     *       keep {@code updatedAt} semantics simple, but with no credential change).
     *   <li>same target, {@code plaintextToken} given ("replace"): the existing row's secret is
     *       re-encrypted and updated in place; no new row is created.
     *   <li>different target (or no existing active connection at all): a brand-new row is created and
     *       activated, and the previous active row (if any) is deactivated, never deleted or mutated beyond
     *       that. A token is required in this case - there is no previous secret to fall back to for a target
     *       Gitea has never been told about.
     * </ul>
     *
     * <p>Both the deactivation of the old row and the activation of the new/updated one happen inside this
     * one {@code @Transactional} method, so a reader never observes either zero or two active rows for the
     * same guild in between.
     */
    @Transactional
    public HolonetProviderConnection activate(
            long guildId,
            HolonetProviderType providerType,
            String baseUrl,
            String owner,
            String repository,
            String plaintextToken) {
        Optional<HolonetProviderConnection> current = this.repository.findByGuildIdAndActiveTrue(guildId);
        boolean sameTarget = current.isPresent()
                && current.get().providerType() == providerType
                && current.get().targets(baseUrl, owner, repository);

        if (sameTarget) {
            HolonetProviderConnection existing = current.get();
            if (plaintextToken != null) {
                existing.updateSecret(encrypt(plaintextToken, guildId, existing.id()), Instant.now(clock));
            }
            return this.repository.save(existing);
        }

        if (plaintextToken == null) {
            throw new IllegalArgumentException(
                    "A token is required when the target base URL, owner, or repository changes");
        }

        UUID newId = UUID.randomUUID();
        Instant now = Instant.now(clock);
        HolonetProviderConnection created = new HolonetProviderConnection(
                newId, guildId, providerType, baseUrl, owner, repository, true,
                encrypt(plaintextToken, guildId, newId), now, now);

        current.ifPresent(HolonetProviderConnection::deactivate);
        current.ifPresent(this.repository::save);
        return this.repository.save(created);
    }

    private EncryptedSecret encrypt(String plaintextToken, long guildId, UUID connectionId) {
        return tokenCryptoService.encrypt(plaintextToken, new TokenEncryptionContext(guildId, connectionId.toString()));
    }
}
