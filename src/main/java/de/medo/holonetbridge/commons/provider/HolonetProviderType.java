package de.medo.holonetbridge.commons.provider;

/**
 * The ticket-provider adapters a {@code HolonetProviderConnection} can target. Gitea is the only implemented
 * adapter; the {@code /setup} provider choice must not offer any value not listed here.
 */
public enum HolonetProviderType {
    GITEA
}
