package de.medo.holonetbridge.commons.provider;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import de.medo.holonetbridge.commons.crypto.EncryptionService;
import de.medo.holonetbridge.commons.domain.HolonetDiscordConfig;
import de.medo.holonetbridge.commons.domain.HolonetSetupFormData;
import de.medo.holonetbridge.commons.extapi.TicketProvider;
import de.medo.holonetbridge.commons.repository.HolonetDiscordConfigRepository;
import de.medo.holonetbridge.commons.service.HolonetBotInteractionSessionService;
import de.medo.holonetbridge.gitea.GiteaProviderFactory;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class HolonetTicketProviderService {

    private final Map<Long, TicketProvider> providers = new ConcurrentHashMap<>();
    private final HolonetDiscordConfigRepository configs;
    private final HolonetBotInteractionSessionService sessions;
    private final EncryptionService encryption;
    private final GiteaProviderFactory gitea;

    public HolonetTicketProviderService(HolonetDiscordConfigRepository configs,
            HolonetBotInteractionSessionService sessions, EncryptionService encryption, GiteaProviderFactory gitea) {
        this.configs = configs;
        this.sessions = sessions;
        this.encryption = encryption;
        this.gitea = gitea;
    }

    @PostConstruct
    public void restoreActiveProviders() {
        // Load separately so even a corrupt provider enum in one row cannot prevent other guilds starting.
        for (Long guildId : configs.findAllGuildIds()) {
            try {
                HolonetDiscordConfig config = configs.findById(guildId).orElseThrow();
                providers.put(guildId, create(config.getTicketProvider(), config.getBaseUrl(),
                        config.getToken(), config.getTarget()));
            } catch (RuntimeException failure) {
                log.warn("Could not restore ticket provider for guildId={}; check the saved configuration", guildId);
            }
        }
    }

    public Optional<TicketProvider> providerFor(long guildId) {
        return Optional.ofNullable(providers.get(guildId));
    }

    public TicketProvider candidateFor(HolonetSetupFormData form) {
        return create(form.getTicketProvider(), form.getBaseUrl(), form.getToken(), form.getTarget());
    }

    public synchronized void completeSetup(HolonetSetupFormData form, HealthCheckResult healthCheck,
            TicketProvider candidate) {
        if (!healthCheck.successful()) {
            throw new IllegalArgumentException("A successful connection test is required");
        }
        java.util.Objects.requireNonNull(candidate, "Provider candidate is required");
        long guildId = form.getSession().getGuildId();
        // The transactional service proxy returns only after commit. Serialize that commit and the swap.
        sessions.completeSetup(form, healthCheck);
        providers.put(guildId, candidate);
    }

    private TicketProvider create(de.medo.holonetbridge.commons.domain.TicketProvider provider,
            String baseUrl, String encryptedToken, String target) {
        return switch (provider) {
            case GITEA -> gitea.create(baseUrl, encryption.decrypt(encryptedToken), target);
            case JIRA, GITHUB, TRELLO -> throw new UnsupportedOperationException("Provider is not implemented");
        };
    }
}
