package de.medo.holonetbridge.commons.repository;

import de.medo.holonetbridge.commons.domain.HolonetBotInteractionSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Limit;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface HolonetBotInteractionSessionRepository
        extends JpaRepository<HolonetBotInteractionSession, Long> {

    @Query("""
            select session
            from HolonetBotInteractionSession session
            where session.guildId = :guildId
              and session.memberId = :memberId
              and session.closedAt is null
            order by session.updatedAt desc, session.id desc
            """)
    Optional<HolonetBotInteractionSession> findLatestOpenSession(
            @Param("guildId") Long guildId,
            @Param("memberId") Long memberId,
            Limit limit);
}
