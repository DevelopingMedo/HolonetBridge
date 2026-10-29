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
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

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

    /** Bei jedem /setup haben gespeicherte Server-Einstellungen Vorrang vor dem persönlichen Entwurf. */
    @Transactional
    public HolonetSetupFormData openSetup(long guildId, long memberId, Locale locale) {
        var config = configRepository.findById(guildId);
        var form = getOrCreateSetupFormDataSession(guildId, memberId, locale);
        config.ifPresent(value -> {
            copyToForm(value, form);
            form.setConfigLoaded(true);
        });
        return form;
    }

    @Transactional
    public HolonetSetupFormData getOrCreateSetupFormDataSession(long guildId, long memberId, Locale locale) {
        var existing = formDataRepository
                .findFirstBySessionGuildIdAndSessionMemberIdAndSessionClosedAtIsNullOrderBySessionUpdatedAtDesc(guildId, memberId);
        if (existing.isPresent()) {
            var form = existing.get();
            var session = form.getSession();
            if (session.getUpdatedAt().isAfter(Instant.now().minus(SETUP_IDLE_TIMEOUT))) {
                session.setLocale(locale);
                session.touch();
                return form;
            }
            session.setClosedAt(Instant.now());
        }
        // Setup ist server- und mitgliedsbezogen; der Aufrufkanal gehört nicht zur Identität.
        var session = createAndSaveSession(guildId, memberId, null, locale);
        var form = createAndSaveSetupFormData(session);
        configRepository.findById(guildId).ifPresent(config -> copyToForm(config, form));
        return form;
    }

    private void copyToForm(HolonetDiscordConfig config, HolonetSetupFormData form) {
        form.setTicketProvider(config.getTicketProvider());
        form.setBaseUrl(config.getBaseUrl());
        form.setTarget(config.getTarget());
        form.setToken(config.getToken());
        form.setSupportChannelId(config.getSupportChannelId());
    }

    /** Erst ein erfolgreicher Verbindungstest macht aus dem Entwurf die gültige Server-Konfiguration. */
    @Transactional
    public void completeSetup(HolonetSetupFormData form) {
        var config = configRepository.findById(form.getSession().getGuildId())
                .orElseGet(HolonetDiscordConfig::new);
        config.setGuildId(form.getSession().getGuildId());
        config.setTicketProvider(form.getTicketProvider());
        config.setBaseUrl(form.getBaseUrl());
        config.setTarget(form.getTarget());
        config.setToken(form.getToken());
        config.setSupportChannelId(form.getSupportChannelId());
        configRepository.save(config);
        form.getSession().setClosedAt(Instant.now());
        sessionRepository.save(form.getSession());
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
        formDataRepository.save(setupFormData);
        setupFormData.getSession().touch();
        sessionRepository.save(setupFormData.getSession());
    }


    @Transactional
    public HolonetReportFormData getOrCreate(long guildId, long memberId, long channelId,
                                             Locale locale, HolonetTicketKind kind) {
        var session = getOrCreateBotInteractionSession(guildId, memberId, channelId, locale);
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
