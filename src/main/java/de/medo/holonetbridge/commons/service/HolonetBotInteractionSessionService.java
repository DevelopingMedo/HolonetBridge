package de.medo.holonetbridge.commons.service;

import de.medo.holonetbridge.commons.domain.HolonetBotInteractionSession;
import de.medo.holonetbridge.commons.domain.HolonetReportFormData;
import de.medo.holonetbridge.commons.domain.HolonetSetupFormData;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicketKind;
import de.medo.holonetbridge.commons.repository.HolonetBotInteractionSessionRepository;
import de.medo.holonetbridge.commons.repository.HolonetReportFormDataRepository;
import de.medo.holonetbridge.commons.repository.HolonetSetupFormDataRepository;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class HolonetBotInteractionSessionService {

    private final HolonetBotInteractionSessionRepository sessionRepository;
    private final HolonetSetupFormDataRepository formDataRepository;
    private final HolonetReportFormDataRepository reportFormDataRepository;


    public HolonetBotInteractionSessionService(
            HolonetBotInteractionSessionRepository sessionRepository,
            HolonetSetupFormDataRepository formDataRepository,
        HolonetReportFormDataRepository reportFormDataRepository) {
        this.sessionRepository = sessionRepository;
        this.formDataRepository = formDataRepository;
        this.reportFormDataRepository = reportFormDataRepository;
    }

    @Transactional
    public HolonetSetupFormData getOrCreateSetupFormDataSession(long guildId, long memberId, Long channelId, Locale locale) {
        HolonetBotInteractionSession session = getOrCreateBotInteractionSession(guildId, memberId, channelId, locale);
        return formDataRepository.findBy(session.getId())
                .orElseGet(() -> createAndSaveSetupFormData(session));
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

    public void updateSetupFormDataSession(long guildId, long memberId,Long channelId, Locale locale, TextChannel supportChannelId) {
        HolonetSetupFormData setupFormData = getOrCreateSetupFormDataSession(guildId,memberId, channelId, locale);
        setupFormData.setSupportChannelId(supportChannelId.getIdLong());
    }

    @Transactional
    public void saveSetupFormDataSession(HolonetSetupFormData setupFormData) {
        formDataRepository.save(setupFormData);
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
