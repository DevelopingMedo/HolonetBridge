package de.medo.holonetbridge.gitea;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Identifies the single Gitea repository that this bridge instance files tickets (Gitea issues) into, since
 * Gitea's issue API is scoped per repository ({@code /repos/{owner}/{repo}/issues}).
 *
 * <p>Composed into {@link GiteaTicketProvider} alongside {@link
 * de.medo.holonetbridge.commons.ProviderConnectionProperties} rather than extending it, because that type is a
 * record and cannot be extended.
 */
@ConfigurationProperties(prefix = "holonet.provider.gitea")
@Validated
public record GiteaProperties(@NotBlank String owner, @NotBlank String repository) {}
