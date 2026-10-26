package de.medo.holonetbridge.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.medo.holonetbridge.HolonetBridgeApplication;
import de.medo.holonetbridge.commons.ProviderConnectionProperties;
import de.medo.holonetbridge.commons.provider.HolonetProviderConnectionService;
import net.dv8tion.jda.api.JDA;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Proves the bot starts up with no provider configured at all - only the mandatory Discord token - since
 * every guild's provider connection is now set up afterwards through {@code /setup}
 * ({@link HolonetProviderConnectionService}) rather than being a startup-time global/single-provider
 * configuration. {@link ProviderConnectionProperties} must not be bound at this point either: it is dev-seeder
 * input only (see its Javadoc) and binding it unconditionally would defeat the point of this test by making a
 * provider mandatory again.
 */
@SpringBootTest(classes = HolonetBridgeApplication.class, properties = "holonet.discord.token=test-discord-token")
class BootstrapSmokeTest {

    // Replaces the real JDA gateway bean so the test does not attempt a live Discord login.
    @MockitoBean
    private JDA jda;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private HolonetProviderConnectionService providerConnectionService;

    @Test
    void startsSuccessfullyWithNoProviderConnectionConfiguredAtAll() {
        assertThat(applicationContext.getBean(HolonetProviderConnectionService.class))
                .isSameAs(providerConnectionService);
        assertThatThrownBy(() -> applicationContext.getBean(ProviderConnectionProperties.class))
                .as("ProviderConnectionProperties must stay unbound outside the (not yet implemented) dev seeder")
                .isInstanceOf(NoSuchBeanDefinitionException.class);
    }
}
