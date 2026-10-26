package de.medo.holonetbridge.commons.discord.ticketing;

/**
 * Minimal lifecycle of a {@link HolonetSupportChannel}'s assignment to its ticket/connection. This is
 * deliberately not a full state machine (archiving, resolution, etc. are a later work package): it only
 * distinguishes "last known usable" from "last attempt to use this channel's assignment failed", so a
 * broken assignment can be surfaced to the user instead of silently retried or papered over.
 */
public enum HolonetSupportChannelStatus {

    /** The channel's assignment was usable the last time it was used (or has never been tried yet). */
    OPEN,

    /**
     * The channel's assignment could not be used the last time it was tried - either no provider connection
     * was resolvable for the guild at the time, or Discord confirmed the channel itself is no longer
     * reachable (deleted, or the bot lost access/permissions). Reset back to {@link #OPEN} automatically the
     * next time a ticket is successfully created and confirmed from this channel.
     */
    BROKEN
}
