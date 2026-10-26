package de.medo.holonetbridge.commons.net;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Operator-controlled allowlist of internal (otherwise SSRF-blocked) hosts that provider connections are
 * permitted to reach, for self-hosting setups where the provider instance lives on a private network next to
 * this application (for example, a Gitea container on the same Docker network). Each entry is an exact
 * {@code host:port} pair, matched case-insensitively on the host.
 *
 * <p>This is deliberately operator-only configuration (environment variables, supplied the same way as the
 * other {@code holonet.*} settings), never something a guild's {@code /setup} input can add to or widen:
 * {@link SsrfSafeUrlValidator} only ever reads this list, nothing in the setup flow writes to it.
 */
@ConfigurationProperties(prefix = "holonet.network.ssrf")
public record SsrfPolicyProperties(List<String> allowedInternalTargets) {

    public SsrfPolicyProperties {
        allowedInternalTargets = allowedInternalTargets == null ? List.of() : List.copyOf(allowedInternalTargets);
    }
}
