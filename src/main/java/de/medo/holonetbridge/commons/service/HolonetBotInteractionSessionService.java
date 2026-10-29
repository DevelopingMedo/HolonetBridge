package de.medo.holonetbridge.commons.service;

import de.medo.holonetbridge.commons.domain.HolonetBotInteractionSession;
import de.medo.holonetbridge.commons.domain.HolonetDiscordConfig;
import de.medo.holonetbridge.commons.domain.HolonetReportFormData;
import de.medo.holonetbridge.commons.domain.HolonetSetupFormData;
import de.medo.holonetbridge.commons.domain.TicketProvider;
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
    public SetupForm openSetup(long guildId, long memberId, Locale locale) {
        var config = configRepository.findById(guildId);
        if (config.isPresent()) {
            // Existing server settings always populate a fresh form, without creating a draft session.
            var form = new HolonetSetupFormData();
            copyToForm(config.get(), form);
            return new SetupForm(form, config.get().getTicketProvider());
        }
        return new SetupForm(getOrCreateSetupFormDataSession(guildId, memberId, locale), null);
    }

    public record SetupForm(HolonetSetupFormData formData, TicketProvider configuredProvider) {}

    @Transactional
    public HolonetSetupFormData getOrCreateSetupFormDataSession(long guildId, long memberId, Locale locale) {
        var existing = formDataRepository
                .findLatestOpenDraft(guildId, memberId, Limit.of(1));
        if (existing.isPresent()) {
            var form = existing.get();
            var session = form.getSession();
            if (session.getUpdatedAt().isAfter(Instant.now().minus(SETUP_IDLE_TIMEOUT))) {
                session.setLocale(locale);
                session.update();
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
        setupFormData.getSession().update();
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
