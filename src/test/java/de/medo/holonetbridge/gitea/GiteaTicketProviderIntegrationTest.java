package de.medo.holonetbridge.gitea;

import static org.assertj.core.api.Assertions.assertThat;

import de.medo.holonetbridge.commons.ProviderConnectionProperties;
import de.medo.holonetbridge.commons.extapi.TicketDto;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end test of {@link GiteaTicketProvider} against a real Gitea instance started via Testcontainers, mirroring
 * the init steps from {@code .ci/deployment/docker-compose.yml} (admin user, access token, repository).
 */
@Testcontainers(disabledWithoutDocker = true)
class GiteaTicketProviderIntegrationTest {

    private static final String ADMIN_USERNAME = "holonet";
    private static final String ADMIN_PASSWORD = "dev-only-not-a-real-secret";
    private static final String REPOSITORY = "support";

    @Container
    private static final GenericContainer<?> GITEA = new GenericContainer<>(DockerImageName.parse("gitea/gitea:1.22"))
            .withEnv("USER_UID", "1000")
            .withEnv("USER_GID", "1000")
            .withEnv("GITEA__database__DB_TYPE", "sqlite3")
            .withEnv("GITEA__security__INSTALL_LOCK", "true")
            .withEnv("GITEA__security__SECRET_KEY", "dev-only-not-a-real-secret")
            .withExposedPorts(3000)
            .waitingFor(Wait.forHttp("/api/healthz").forPort(3000).forStatusCode(200));

    private static GiteaTicketProvider provider;

    @BeforeAll
    static void setUpProvider() throws Exception {
        createAdminUser();
        String token = generateAccessToken();
        createRepository(token);
        URI baseUrl = URI.create("http://" + GITEA.getHost() + ":" + GITEA.getMappedPort(3000));
        provider = new GiteaTicketProvider(
                RestClient.builder(),
                new ProviderConnectionProperties("gitea", baseUrl, token),
                new GiteaProperties(ADMIN_USERNAME, REPOSITORY));
    }

    @Test
    void createsAnIssueInTheRunningGiteaInstance() {
        TicketDto ticket = provider.create(
                "[Bug] Login fails",
                "**Was hast du vor dem Fehler gemacht?**\nEingeloggt und gewartet");

        assertThat(ticket.id()).isNotBlank();
        assertThat(ticket.title()).isEqualTo("[Bug] Login fails");
        assertThat(ticket.description())
                .isEqualTo("**Was hast du vor dem Fehler gemacht?**\nEingeloggt und gewartet");
        assertThat(ticket.url())
                .as("Gitea's html_url must come through as the ticket's browser-facing url")
                .isNotBlank()
                .contains("/" + ADMIN_USERNAME + "/" + REPOSITORY + "/issues/" + ticket.id());
    }

    private static void createAdminUser() throws Exception {
        exec("su-exec", "1000:1000", "gitea", "admin", "user", "create",
                "--username", ADMIN_USERNAME,
                "--password", ADMIN_PASSWORD,
                "--email", ADMIN_USERNAME + "@holonet.invalid",
                "--admin",
                "--must-change-password=false");
    }

    private static String generateAccessToken() throws Exception {
        return exec("su-exec", "1000:1000", "gitea", "admin", "user", "generate-access-token",
                "--username", ADMIN_USERNAME,
                "--token-name", "holonet-bridge",
                "--scopes", "all",
                "--raw").trim();
    }

    private static void createRepository(String token) {
        RestClient restClient = RestClient.builder()
                .baseUrl("http://" + GITEA.getHost() + ":" + GITEA.getMappedPort(3000))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "token " + token)
                .build();
        restClient.post()
                .uri("/api/v1/user/repos")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", REPOSITORY, "private", true, "auto_init", true))
                .retrieve()
                .toBodilessEntity();
    }

    private static String exec(String... command) throws Exception {
        var result = GITEA.execInContainer(command);
        assertThat(result.getExitCode())
                .as("command failed: %s%n%s", String.join(" ", command), result.getStderr())
                .isZero();
        return result.getStdout();
    }
}
