package de.medo.holonetbridge.commons.repository;

import java.util.List;
import java.util.Optional;

import de.medo.holonetbridge.commons.domain.HolonetSetupFormData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HolonetSetupFormDataRepository
        extends JpaRepository<HolonetSetupFormData, Long> {

    @Query("""
            select formData
            from HolonetSetupFormData formData
            where formData.session.id = :sessionId
            """)
    Optional<HolonetSetupFormData> findBy(@Param("sessionId") Long sessionId);

    List<HolonetSetupFormData> findBySessionGuildId(long guildId);
}
