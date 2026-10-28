package de.medo.holonetbridge.commons.provider;

import java.net.URI;

import de.medo.holonetbridge.commons.domain.TicketProvider;

public interface ProviderHealthCheck {

    TicketProvider provider();

    /** Checks authenticated connectivity without creating or changing provider resources. */
    void check(URI baseUrl, String token);
}
