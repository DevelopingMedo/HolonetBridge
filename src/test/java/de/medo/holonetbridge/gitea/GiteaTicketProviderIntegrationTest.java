package de.medo.holonetbridge.gitea;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.medo.holonetbridge.commons.domain.ticket.HolonetTicketKind;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.context.MessageSource;
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
    private static RestClient verificationClient;

    @BeforeAll
    static void setUpProvider() throws Exception {
        createAdminUser();
        String token = generateAccessToken();
        createRepository(token);
        URI baseUrl = URI.create("http://" + GITEA.getHost() + ":" + GITEA.getMappedPort(3000));
        verificationClient = RestClient.builder().baseUrl(baseUrl.toString())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "token " + token).build();
        provider = new GiteaTicketProvider(RestClient.builder(), baseUrl, token, ADMIN_USERNAME, REPOSITORY,
                mock(MessageSource.class));
    }

    @Test
    void createsAnIssueInTheRunningGiteaInstance() {
        var form = GiteaTicketProviderTest.form(HolonetTicketKind.FEATURE);
        form.setDescription("**Original**\nEingeloggt und gewartet");
        provider.create(form);

        var issues = verificationClient.get().uri("/api/v1/repos/{owner}/{repo}/issues", ADMIN_USERNAME, REPOSITORY)
                .retrieve().body(java.util.List.class);
        assertThat(issues).hasSize(1);
        var issue = (Map<?, ?>) issues.getFirst();
        assertThat(issue.get("title")).isEqualTo("[Feature] Login fails");
        assertThat(issue.get("body").toString()).startsWith("**Original**\nEingeloggt und gewartet");
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
        assertThat(result.getExitCode()).as("Gitea setup command failed").isZero();
        return result.getStdout();
    }
}
