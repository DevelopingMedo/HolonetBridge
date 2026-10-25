package de.medo.holonetbridge.commons;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ProviderConnectionPropertiesTest {

    @Test
    void keepsConnectionValuesAndRedactsTokenFromStringRepresentation() {
        var properties = new ProviderConnectionProperties(
                "gitea", URI.create("https://tickets.example.invalid"), "sensitive-test-token");

        assertThat(properties.id()).isEqualTo("gitea");
        assertThat(properties.baseUrl()).isEqualTo(URI.create("https://tickets.example.invalid"));
        assertThat(properties.token()).isEqualTo("sensitive-test-token");
        assertThat(properties.toString()).contains("[REDACTED]").doesNotContain("sensitive-test-token");
    }

    @Test
    void rejectsNonHttpProviderUrls() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ProviderConnectionProperties("gitea", URI.create("file:///tmp/tickets"), "token"));
    }

    @Test
    void rejectsMissingRequiredConfigurationAtStartup() {
        new ApplicationContextRunner()
                .withUserConfiguration(ProviderConfiguration.class)
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNotNull();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(BindValidationException.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ProviderConnectionProperties.class)
    static class ProviderConfiguration {}
}
