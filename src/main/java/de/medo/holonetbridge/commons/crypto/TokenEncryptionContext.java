package de.medo.holonetbridge.commons.crypto;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * The authenticated-but-not-encrypted context (AES-GCM "associated data") a token's ciphertext is bound
 * to: the Discord guild and the provider connection it belongs to. Binding both means a ciphertext row that
 * is copied or re-pointed to a different guild or connection (whether by a bug or by direct database
 * tampering) fails to decrypt instead of silently decrypting into a token that is then used in the wrong
 * guild's context. The provider-connection entity itself is introduced in a later work package; this type
 * only fixes the canonical AAD shape so that entity can bind to it without redefining it.
 */
public record TokenEncryptionContext(long guildId, String connectionId) {

    public TokenEncryptionContext {
        Objects.requireNonNull(connectionId, "connectionId must not be null");
    }

    byte[] associatedData() {
        return (guildId + ":" + connectionId).getBytes(StandardCharsets.UTF_8);
    }
}
