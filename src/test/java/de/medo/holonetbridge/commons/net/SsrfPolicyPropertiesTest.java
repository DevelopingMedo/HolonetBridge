package de.medo.holonetbridge.commons.net;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.Configuration;

/**
 * Proves that the self-hosting allowlist can actually be configured the way the shipped
 * {@code docker-compose.yml} does it: a single environment variable holding a comma-separated list of
 * {@code host:port} entries. Spring Boot only comma-splits a single environment-variable value into a list
 * when the source is recognised as an OS environment property source, so these tests bind the value via
 * {@link TestPropertyValues.Type#SYSTEM_ENVIRONMENT} instead of a plain property, which would not exercise
 * that splitting behaviour and could hide a binding regression that the compose file would only surface at
 * container start.
 */
class SsrfPolicyPropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void isAllowlistedOnlyMatchesTheConfiguredTargetAfterRealEnvironmentVariableBinding() {
        withEnvironmentVariable("HOLONET_NETWORK_SSRF_ALLOWED_INTERNAL_TARGETS=gitea:3000").run(context -> {
            SsrfPolicyProperties properties = context.getBean(SsrfPolicyProperties.class);
            assertThat(properties.allowedInternalTargets()).containsExactly("gitea:3000");

            SsrfSafeUrlValidator validator = new SsrfSafeUrlValidator(properties);
            assertThat(validator.isAllowlisted("gitea", 3000)).isTrue();
            assertThat(validator.isAllowlisted("gitea", 9999)).isFalse();
            assertThat(validator.isAllowlisted("some-other-internal-host", 3000)).isFalse();
        });
    }

    @Test
    void splitsMultipleEntriesOfTheSameEnvironmentVariableByComma() {
        withEnvironmentVariable("HOLONET_NETWORK_SSRF_ALLOWED_INTERNAL_TARGETS=gitea:3000,other-internal-host:8080")
                .run(context -> assertThat(
                                context.getBean(SsrfPolicyProperties.class).allowedInternalTargets())
                        .containsExactly("gitea:3000", "other-internal-host:8080"));
    }

    @Test
    void defaultsToAnEmptyAllowlistWhenNothingIsConfigured() {
        contextRunner.run(context -> {
            SsrfPolicyProperties properties = context.getBean(SsrfPolicyProperties.class);
            assertThat(properties.allowedInternalTargets()).isEmpty();
        });
    }

    private ApplicationContextRunner withEnvironmentVariable(String pair) {
        return contextRunner.withInitializer(applicationContext -> TestPropertyValues.of(pair)
                .applyTo(applicationContext.getEnvironment(), TestPropertyValues.Type.SYSTEM_ENVIRONMENT));
    }

    @Configuration
    @EnableConfigurationProperties(SsrfPolicyProperties.class)
    static class PropertiesConfiguration {}
}
