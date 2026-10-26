package de.medo.holonetbridge.commons.discord.ticketing;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HolonetGuildSequenceRepository extends JpaRepository<HolonetGuildSequence, Long> {

    /**
     * Acquires an exclusive, commit-held row lock on the guild's counter (translates to {@code SELECT ...
     * FOR UPDATE}) - the actual mutual-exclusion guarantee {@link HolonetGuildSequenceTransaction} relies on
     * before calling {@link #increment}. A blind {@code UPDATE s.currentNumber = s.currentNumber + 1} alone
     * is not sufficient for that on every database: without first establishing this lock, two concurrent
     * transactions can each still base their {@code + 1} on the same pre-update value and both commit the
     * same "next" number instead of one of them blocking until the other has committed.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from HolonetGuildSequence s where s.guildId = :guildId")
    Optional<HolonetGuildSequence> lockForUpdate(@Param("guildId") long guildId);

    /**
     * Increments the guild's counter in place. Safe against concurrent callers for the same guild only once
     * the caller already holds the row's write lock via {@link #lockForUpdate}; on its own, this statement's
     * row lock is held only for the instant of the {@code UPDATE} itself, not for the whole transaction.
     * Returns the number of rows updated (0 if the guild has no row yet) so the caller can tell "no such
     * guild yet" apart from "updated".
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update HolonetGuildSequence s set s.currentNumber = s.currentNumber + 1 where s.guildId = :guildId")
    int increment(@Param("guildId") long guildId);
}
