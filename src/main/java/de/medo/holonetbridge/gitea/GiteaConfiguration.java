package de.medo.holonetbridge.gitea;

import de.medo.holonetbridge.commons.ProviderConnectionProperties;
import de.medo.holonetbridge.commons.ticket.TicketProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Wires up the Gitea ticket-provider adapter, gated on {@code holonet.provider.id=gitea}. A future second
 * provider adapter follows the same pattern: its own {@code @Configuration} class, gated on its own
 * {@code holonet.provider.id} value.
 */
@Configuration
@ConditionalOnProperty(prefix = "holonet.provider", name = "id", havingValue = "gitea")
@EnableConfigurationProperties(GiteaProperties.class)
class GiteaConfiguration {

    @Bean
    TicketProvider giteaTicketProvider(
            RestClient.Builder restClientBuilder,
            ProviderConnectionProperties providerConnectionProperties,
            GiteaProperties giteaProperties) {
        return new GiteaTicketProvider(restClientBuilder, providerConnectionProperties, giteaProperties);
    }
}
