package de.medo.holonetbridge.commons.service;

import de.medo.holonetbridge.commons.domain.HolonetBotInteractionSession;
import de.medo.holonetbridge.commons.domain.HolonetDiscordConfig;
import de.medo.holonetbridge.commons.domain.HolonetReportFormData;
import de.medo.holonetbridge.commons.domain.HolonetSetupFormData;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicketKind;
import de.medo.holonetbridge.commons.repository.HolonetBotInteractionSessionRepository;
import de.medo.holonetbridge.commons.repository.HolonetDiscordConfigRepository;
import de.medo.holonetbridge.commons.repository.HolonetReportFormDataRepository;
import de.medo.holonetbridge.commons.repository.HolonetSetupFormDataRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Limit;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

@Service
public class HolonetBotInteractionSessionService {

    private static final Duration SETUP_IDLE_TIMEOUT = Duration.ofHours(1);
    private final HolonetDiscordConfigRepository configRepository;
    private final HolonetBotInteractionSessionRepository sessionRepository;
    private final HolonetSetupFormDataRepository formDataRepository;
    private final HolonetReportFormDataRepository reportFormDataRepository;


    public HolonetBotInteractionSessionService(
            HolonetBotInteractionSessionRepository sessionRepository,
            HolonetSetupFormDataRepository formDataRepository,
            HolonetReportFormDataRepository reportFormDataRepository,
            HolonetDiscordConfigRepository configRepository) {
        this.configRepository = configRepository;
        this.sessionRepository = sessionRepository;
        this.formDataRepository = formDataRepository;
        this.reportFormDataRepository = reportFormDataRepository;
    }

    @Transactional
    public HolonetSetupFormData getOrCreateSetupFormDataSession(long guildId, long memberId, Locale locale) {
        Optional<HolonetDiscordConfig> config = configRepository.findById(guildId);
        if (config.isPresent()) {
            return createSetupFormData(config.get(), memberId, locale);
        }
        Optional<HolonetSetupFormData> existing = formDataRepository
                .findLatestOpenSetupFormData(guildId, memberId, Limit.of(1));
        if (existing.isPresent()) {
            HolonetSetupFormData form = existing.get();
            HolonetBotInteractionSession session = form.getSession();
            if (session.getUpdatedAt().isAfter(Instant.now().minus(SETUP_IDLE_TIMEOUT))) {
                session.setLocale(locale);
                session.update();
                return form;
            }
            session.setClosedAt(Instant.now());
        }
        HolonetBotInteractionSession session = createAndSaveSession(guildId, memberId, null, locale);
        return createAndSaveSetupFormData(session);
    }

    private HolonetSetupFormData createSetupFormData(HolonetDiscordConfig config, long memberId, Locale locale) {
        var session = new HolonetBotInteractionSession();
        session.setGuildId(config.getGuildId());
        session.setMemberId(memberId);
        session.setLocale(locale);
        var form = new HolonetSetupFormData();
        // Loading a config creates no database records; the transient session carries the interaction context.
        form.setSession(session);
        form.setTicketProvider(config.getTicketProvider());
        form.setBaseUrl(config.getBaseUrl());
        form.setTarget(config.getTarget());
        form.setToken(config.getToken());
        form.setSupportChannelId(config.getSupportChannelId());
        return form;
    }

    @Transactional
    public void completeSetup(HolonetSetupFormData form) {
        HolonetDiscordConfig config = configRepository.findById(form.getSession().getGuildId())
                .orElseGet(HolonetDiscordConfig::new);
        config.setGuildId(form.getSession().getGuildId());
        config.setTicketProvider(form.getTicketProvider());
        config.setBaseUrl(form.getBaseUrl());
        config.setTarget(form.getTarget());
        config.setToken(form.getToken());
        config.setSupportChannelId(form.getSupportChannelId());
        configRepository.save(config);
        form.getSession().setClosedAt(Instant.now());
        if (form.getSession().getId() != null) {
            sessionRepository.save(form.getSession());
        }
    }

    @Transactional
    public @NonNull HolonetBotInteractionSession getOrCreateBotInteractionSession(long guildId, long memberId, Long channelId, Locale locale) {
        return sessionRepository
                .findBy(guildId, memberId, channelId)
                .orElseGet(() -> createAndSaveSession(guildId, memberId, channelId, locale));
    }

    private HolonetBotInteractionSession createAndSaveSession(long guildId, long memberId, Long channelId, Locale locale) {
        HolonetBotInteractionSession session = new HolonetBotInteractionSession();
        session.setGuildId(guildId);
        session.setMemberId(memberId);
        session.setChannelId(channelId);
        session.setLocale(locale);
        return sessionRepository.save(session);
    }

    private HolonetSetupFormData createAndSaveSetupFormData(HolonetBotInteractionSession session) {
        HolonetSetupFormData formData = new HolonetSetupFormData();
        formData.setSession(session);

        return formDataRepository.save(formData);
    }

    @Transactional
    public void saveSetupFormDataSession(HolonetSetupFormData setupFormData) {
        setupFormData.getSession().update();
        setupFormData.setSession(sessionRepository.save(setupFormData.getSession()));
        formDataRepository.save(setupFormData);
    }


    @Transactional
    public HolonetReportFormData getOrCreate(long guildId, long memberId, long channelId,
                                             Locale locale, HolonetTicketKind kind) {
        HolonetBotInteractionSession session = getOrCreateBotInteractionSession(guildId, memberId, channelId, locale);
        return reportFormDataRepository.findBySessionIdAndKind(session.getId(), kind).orElseGet(() -> {
            var data = new HolonetReportFormData();
            data.setSession(session);
            data.setKind(kind);
            return data;
        });
    }

    @Transactional
    public void save(HolonetReportFormData data) {
        reportFormDataRepository.saveAndFlush(data);
    }
}
