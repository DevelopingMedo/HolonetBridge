package de.medo.holonetbridge.commons.provider;

import java.time.Instant;
import java.util.UUID;

import de.medo.holonetbridge.commons.crypto.EncryptedSecret;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * One guild's ticket-provider target (base URL, owner/repository, and an encrypted access token) and whether
 * it is the guild's currently active connection. See {@link HolonetProviderConnectionService} for the
 * invariants a row here must only ever be created/activated/rotated through: at most one active row per
 * guild, a target (base URL/owner/repository) change always creates a new row instead of mutating this one,
 * and a token-only change on the same target updates this row's secret in place.
 *
 * <p>An inactive row is never deleted by that service: tickets already filed against it (see the later
 * ticket-to-connection assignment work) still need to resolve back to the connection they were actually
 * created against, even after the guild has since moved on to a different one.
 */
@Entity
@Table(name = "holonet_provider_connection")
public class HolonetProviderConnection {

    @Id
    private UUID id;

    private long guildId;

    @Enumerated(EnumType.STRING)
    private HolonetProviderType providerType;

    private String baseUrl;
    private String owner;
    private String repository;
    private boolean active;

    @Lob
    private byte[] secretCiphertext;

    @Lob
    private byte[] secretNonce;

    private String secretKeyVersion;

    private Instant createdAt;
    private Instant updatedAt;

    protected HolonetProviderConnection() {}

    public HolonetProviderConnection(
            UUID id,
            long guildId,
            HolonetProviderType providerType,
            String baseUrl,
            String owner,
            String repository,
            boolean active,
            EncryptedSecret secret,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.guildId = guildId;
        this.providerType = providerType;
        this.baseUrl = baseUrl;
        this.owner = owner;
        this.repository = repository;
        this.active = active;
        applySecret(secret);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID id() {
        return id;
    }

    public long guildId() {
        return guildId;
    }

    public HolonetProviderType providerType() {
        return providerType;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String owner() {
        return owner;
    }

    public String repository() {
        return repository;
    }

    public boolean active() {
        return active;
    }

    public EncryptedSecret encryptedSecret() {
        return new EncryptedSecret(secretCiphertext, secretNonce, secretKeyVersion);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    /** Whether {@code baseUrl}/{@code owner}/{@code repository} are exactly this connection's own target. */
    public boolean targets(String baseUrl, String owner, String repository) {
        return this.baseUrl.equals(baseUrl) && this.owner.equals(owner) && this.repository.equals(repository);
    }

    void deactivate() {
        this.active = false;
    }

    void activate() {
        this.active = true;
    }

    /** Rotates the encrypted secret in place (same connection identity, same target) and bumps {@code updatedAt}. */
    void updateSecret(EncryptedSecret secret, Instant updatedAt) {
        applySecret(secret);
        this.updatedAt = updatedAt;
    }

    private void applySecret(EncryptedSecret secret) {
        this.secretCiphertext = secret.ciphertext();
        this.secretNonce = secret.nonce();
        this.secretKeyVersion = secret.keyVersion();
    }
}
