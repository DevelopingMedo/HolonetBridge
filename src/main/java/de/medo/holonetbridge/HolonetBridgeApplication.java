package de.medo.holonetbridge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * {@code @ConfigurationPropertiesScan} is narrowed to {@code commons}: provider-specific properties types
 * (such as Gitea's {@code GiteaProperties}) are registered by their own conditional {@code @Configuration}
 * class instead, since they only apply when that provider is selected.
 */
@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = "de.medo.holonetbridge.commons")
public class HolonetBridgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(HolonetBridgeApplication.class, args);
    }
}
