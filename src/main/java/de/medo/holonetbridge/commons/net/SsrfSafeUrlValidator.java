package de.medo.holonetbridge.commons.net;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Resolves a host to every address DNS actually returns and checks each one against the blocked ranges
 * below, rather than checking only the hostname string. A single blocked address among several resolved ones
 * is enough to reject the whole lookup: silently connecting to whichever of several resolved addresses
 * happens to be safe while ignoring that the same name also resolves to an internal address is itself a
 * rebinding-adjacent risk (the attacker fully controls DNS and can simply stop returning the "safe" address
 * on a later lookup).
 *
 * <p>Callers (see {@code SsrfSafeHttpClientFactory}) must use the addresses returned here directly for the
 * actual connection instead of resolving the host a second time: a validate-then-reconnect-by-hostname
 * design leaves a window for the DNS answer to change between the two lookups (DNS rebinding), which defeats
 * the validation entirely.
 */
@Component
public class SsrfSafeUrlValidator {

    private final SsrfPolicyProperties policy;

    public SsrfSafeUrlValidator(SsrfPolicyProperties policy) {
        this.policy = policy;
    }

    /**
     * Resolves {@code host} and returns only its safe-to-connect-to addresses. An operator-allowlisted
     * {@code host:port} target bypasses the blocked-range check entirely (trusted self-hosting target);
     * otherwise every resolved address must individually pass {@link #isBlockedAddress(InetAddress)}.
     *
     * @throws SsrfBlockedTargetException if every resolved address is blocked (and the target is not
     *     allowlisted), or if {@code host} does not resolve at all
     */
    public List<InetAddress> resolveSafeAddresses(String host, int port) {
        boolean allowlisted = isAllowlisted(host, port);
        InetAddress[] resolved;
        try {
            resolved = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new SsrfBlockedTargetException("Could not resolve host '" + host + "'", e);
        }

        List<InetAddress> safe = allowlisted
                ? List.of(resolved)
                : java.util.Arrays.stream(resolved).filter(address -> !isBlockedAddress(address)).toList();

        if (safe.isEmpty()) {
            throw new SsrfBlockedTargetException(
                    "Host '" + host + "' only resolves to addresses blocked by the provider-connection "
                            + "network policy (loopback/private/link-local/multicast); the self-hosting "
                            + "allowlist does not cover this target either");
        }
        return safe;
    }

    /** Whether {@code host:port} is on the operator's self-hosting allowlist ({@link SsrfPolicyProperties}). */
    public boolean isAllowlisted(String host, int port) {
        String target = host.toLowerCase(Locale.ROOT) + ":" + port;
        return policy.allowedInternalTargets().stream()
                .map(entry -> entry.toLowerCase(Locale.ROOT))
                .anyMatch(target::equals);
    }

    /**
     * True for loopback (127.0.0.0/8, ::1), private IPv4 (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16),
     * link-local (169.254.0.0/16 - including the 169.254.169.254 cloud-metadata endpoint - and fe80::/10),
     * IPv6 unique local (fc00::/7), multicast, and the wildcard/any-local address.
     */
    public boolean isBlockedAddress(InetAddress address) {
        return address.isLoopbackAddress()
                || address.isSiteLocalAddress()
                || address.isLinkLocalAddress()
                || address.isMulticastAddress()
                || address.isAnyLocalAddress()
                || isIpv6UniqueLocal(address);
    }

    private static boolean isIpv6UniqueLocal(InetAddress address) {
        if (!(address instanceof Inet6Address)) {
            return false;
        }
        byte[] bytes = address.getAddress();
        // fc00::/7: the top 7 bits of the first byte are 1111 110.
        return (bytes[0] & 0xFE) == 0xFC;
    }
}
