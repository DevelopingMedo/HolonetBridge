package de.medo.holonetbridge.commons.provider;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import de.medo.holonetbridge.commons.domain.HolonetDiscordConfig;
import de.medo.holonetbridge.commons.domain.HolonetSetupFormData;
import de.medo.holonetbridge.commons.extapi.TicketProvider;
import de.medo.holonetbridge.commons.repository.HolonetDiscordConfigRepository;
import de.medo.holonetbridge.commons.service.HolonetBotInteractionSessionService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class HolonetTicketProviderService {

    private final Map<Long, TicketProvider> providers = new ConcurrentHashMap<>();
    private final HolonetDiscordConfigRepository discordConfigRepository;
    private final HolonetBotInteractionSessionService botInteractionSessionService;
    private final ProviderFactory providerFactory;

    public HolonetTicketProviderService(HolonetDiscordConfigRepository discordConfigRepository,
            HolonetBotInteractionSessionService botInteractionSessionService, ProviderFactory providerFactory) {
        this.discordConfigRepository = discordConfigRepository;
        this.botInteractionSessionService = botInteractionSessionService;
        this.providerFactory = providerFactory;
    }

    @PostConstruct
    public void restoreActiveProviders() {
        // Load separately so even a corrupt provider enum in one row cannot prevent other guilds starting.
        for (Long guildId : discordConfigRepository.findAllGuildIds()) {
            try {
                HolonetDiscordConfig config = discordConfigRepository.findById(guildId).orElseThrow();
                providers.put(guildId, providerFactory.create(config.getTicketProvider(), config.getBaseUrl(),
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
        return providerFactory.create(form.getTicketProvider(), form.getBaseUrl(), form.getToken(), form.getTarget());
    }

    public synchronized void completeSetup(HolonetSetupFormData form, HealthCheckResult healthCheck,
            TicketProvider candidate) {
        if (!healthCheck.successful()) {
            throw new IllegalArgumentException("A successful connection test is required");
        }
        java.util.Objects.requireNonNull(candidate, "Provider candidate is required");
        long guildId = form.getSession().getGuildId();
        // The transactional service proxy returns only after commit. Serialize that commit and the swap.
        botInteractionSessionService.completeSetup(form, healthCheck);
        providers.put(guildId, candidate);
    }
}
