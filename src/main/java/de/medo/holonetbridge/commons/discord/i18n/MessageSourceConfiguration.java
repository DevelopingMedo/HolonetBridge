package de.medo.holonetbridge.commons.discord.i18n;

import java.util.List;
import java.util.Properties;

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.core.io.Resource;

@Configuration
@EnableConfigurationProperties(I18nProperties.class)
public class MessageSourceConfiguration {

    @Bean
    public MessageSource messageSource(I18nProperties properties) {
        ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource() {
            @Override
            protected Properties loadProperties(Resource resource, String filename) {
                YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
                yaml.setResources(resource);
                return yaml.getObject();
            }
        };
        source.setFileExtensions(List.of(".yml"));
        source.setFallbackToSystemLocale(false);
        if (properties.directory() != null) {
            String directory = properties.directory().toAbsolutePath().toUri().toString();
            source.addBasenames(directory + (directory.endsWith("/") ? "" : "/") + "messages");
        }
        source.addBasenames("classpath:i18n/messages");
        return source;
    }
}
