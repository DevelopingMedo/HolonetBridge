package de.medo.holonetbridge.commons.provider;

import java.util.Optional;
import java.util.UUID;

/**
 * Looks up a {@link HolonetProviderConnection} and builds a {@link TicketProvider} for it on demand.
 * Deliberately not a cache in front of a single global provider instance: every resolution decrypts that
 * connection's current token and builds a brand-new client/provider from it, so a token rotation (which
 * changes {@code secretKeyVersion} and, for a target change, the connection id too) is picked up on the very
 * next resolution instead of some arbitrarily-stale cached client still sending the old {@code Authorization}
 * header. The cost of never caching is one extra decrypt and client construction per resolution; for the
 * request volumes this bridge handles, that is a reasonable trade for not having to invalidate a cache
 * correctly on every credential change.
 */
public interface ProviderConnectionResolver {

    /** The given guild's active connection, resolved to a ready-to-use provider, if it has one. */
    Optional<ResolvedProvider> forGuild(long guildId);

    /** One specific connection (active or not), resolved to a ready-to-use provider, if it exists. */
    Optional<ResolvedProvider> forConnection(UUID connectionId);
}
