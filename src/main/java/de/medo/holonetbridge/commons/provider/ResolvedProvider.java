package de.medo.holonetbridge.commons.provider;

import de.medo.holonetbridge.commons.extapi.TicketProvider;

/**
 * A {@link TicketProvider} built fresh from one connection's current (decrypted at the moment of resolution,
 * never cached) credentials, together with the connection it was built for.
 */
public record ResolvedProvider(HolonetProviderConnection connection, TicketProvider ticketProvider) {}
