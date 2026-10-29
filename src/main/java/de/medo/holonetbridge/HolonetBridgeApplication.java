package de.medo.holonetbridge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * {@code @ConfigurationPropertiesScan} is narrowed to the specific {@code commons} subpackages that must
 * always bind successfully at every startup, deliberately excluding the {@code commons} root package itself:
 * {@code ProviderConnectionProperties} lives there but is no longer startup-mandatory configuration (see its
 * Javadoc) - it is {@code @Validated} and would otherwise fail binding on a bot that has no provider
 * connection set up yet, which is exactly the state {@code /setup} needs to be usable from.
 */
@SpringBootApplication
@ConfigurationPropertiesScan(
        basePackages = {
            "de.medo.holonetbridge.commons.discord",
            "de.medo.holonetbridge.commons.crypto",
            "de.medo.holonetbridge.commons.net",
            "de.medo.holonetbridge.commons.provider"
        })
public class HolonetBridgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(HolonetBridgeApplication.class, args);
    }
}
