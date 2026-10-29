package de.medo.holonetbridge.commons.discord.modal;

import java.net.URI;
import java.util.Objects;

import static org.springframework.util.StringUtils.hasText;

import de.medo.holonetbridge.commons.crypto.EncryptionService;
import de.medo.holonetbridge.commons.domain.HolonetReportFormData;
import de.medo.holonetbridge.commons.domain.HolonetSetupFormData;
import de.medo.holonetbridge.commons.domain.TicketProvider;
import de.medo.holonetbridge.commons.extapi.TicketDto;
import de.medo.holonetbridge.commons.repository.HolonetSetupFormDataRepository;
import de.medo.holonetbridge.gitea.GiteaProviderFactory;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

@Component
public class HolonetTicketSubmitter {

    private final HolonetSetupFormDataRepository setupRepository;
    private final GiteaProviderFactory giteaProviderFactory;
    private final EncryptionService encryptionService;
    private final MessageSource messageSource;

    public HolonetTicketSubmitter(HolonetSetupFormDataRepository setupRepository,
            GiteaProviderFactory giteaProviderFactory, EncryptionService encryptionService,
            MessageSource messageSource) {
        this.setupRepository = setupRepository;
        this.giteaProviderFactory = giteaProviderFactory;
        this.encryptionService = encryptionService;
        this.messageSource = messageSource;
    }

    public TicketDto submit(TicketProvider provider, HolonetReportFormData data) {
        Objects.requireNonNull(provider, "Ticket provider is required");
        validateContext(data);
        return switch (provider) {
            case GITEA -> {
                HolonetSetupFormData setup = configurationFor(data);
                if (setup.getTicketProvider() != provider) {
                    throw new IllegalStateException("Requested provider does not match the server configuration");
                }
                String[] target = setup.getTarget() == null ? new String[0] : setup.getTarget().split("/", -1);
                if (target.length != 2 || target[0].isBlank() || target[1].isBlank()) {
                    throw new IllegalStateException("Gitea target must be owner/repository");
                }
                yield giteaProviderFactory.create(URI.create(setup.getBaseUrl()),
                                encryptionService.decrypt(setup.getToken()), target[0], target[1])
                        .create(data);
            }
            case JIRA, GITHUB, TRELLO -> throw new UnsupportedOperationException(
                    "Ticket submission is not implemented for " + provider.getDisplayName());
        };
    }

    public void handle(ModalInteractionEvent event, HolonetReportFormData data) {
        // External submission remains explicit until the provider design is approved.
        event.reply(messageSource.getMessage("ticket.draft-saved", null, event.getUserLocale().toLocale()))
                .setEphemeral(true).queue();
    }

    private HolonetSetupFormData configurationFor(HolonetReportFormData data) {
        // Setup currently has no active-connection model. Never guess between multiple destinations.
        var configurations = setupRepository.findBySessionGuildId(data.getSession().getGuildId()).stream()
                .filter(this::isConfigured)
                .toList();
        if (configurations.size() != 1) {
            throw new IllegalStateException("Exactly one complete provider configuration is required for this server");
        }
        return configurations.getFirst();
    }

    private boolean isConfigured(HolonetSetupFormData setup) {
        return setup.getTicketProvider() != null
                && hasText(setup.getBaseUrl())
                && hasText(setup.getTarget())
                && hasText(setup.getToken())
                && setup.getSupportChannelId() != null;
    }

    private void validateContext(HolonetReportFormData data) {
        Objects.requireNonNull(data, "Report form data is required");
        if (data.getSession() == null || data.getSession().getGuildId() == null
                || data.getSession().getMemberId() == null
                || data.getGuildName() == null || data.getGuildName().isBlank()
                || data.getUsername() == null || data.getUsername().isBlank()) {
            throw new IllegalArgumentException("Discord server name, username and IDs are required");
        }
    }
}
