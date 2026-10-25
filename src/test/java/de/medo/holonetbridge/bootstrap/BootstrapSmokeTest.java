package de.medo.holonetbridge.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import de.medo.holonetbridge.HolonetBridgeApplication;
import de.medo.holonetbridge.commons.ProviderConnectionProperties;
import net.dv8tion.jda.api.JDA;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
        classes = HolonetBridgeApplication.class,
        properties = {
            "holonet.provider.id=gitea",
            "holonet.provider.base-url=https://tickets.example.invalid",
            "holonet.provider.token=test-token",
            "holonet.provider.gitea.owner=acme",
            "holonet.provider.gitea.repository=support",
            "holonet.discord.token=test-discord-token"
        })
class BootstrapSmokeTest {

    // Replaces the real JDA gateway bean so the test does not attempt a live Discord login.
    @MockitoBean
    private JDA jda;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ProviderConnectionProperties properties;

    @Test
    void startsWithAConfiguredSingletonProviderConnection() {
        assertThat(applicationContext.getBean(ProviderConnectionProperties.class)).isSameAs(properties);
        assertThat(properties.id()).isEqualTo("gitea");
        assertThat(properties.baseUrl()).hasToString("https://tickets.example.invalid");
    }
}
