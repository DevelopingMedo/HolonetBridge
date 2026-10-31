package de.medo.holonetbridge.commons.discord.ticketing;

import de.holonetbridge.commons.message.SupportMessageCodes;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;

/** Allowlisted metadata only: never retain a Throwable, message, cause, or response snapshot. */
public record HolonetArchiveDiagnostic(Phase phase, String exceptionClass, ErrorResponse discordError, Integer discordCode,
        BotPermissions bot, Boolean nameMatches, Boolean overridesMatch) {
    public enum Phase {
        BOT_PREFLIGHT, CHANNEL_GET, GET_PARSE,
        CHANNEL_PATCH, PATCH_PARSE, CONFIRMATION_MISMATCH
    }

    public record BotPermissions(boolean view, boolean manageChannel, boolean managePermissions, boolean admin) {
        public List<String> missingMessageKeys() {
            var missing = new ArrayList<String>();
            if (!view) missing.add(SupportMessageCodes.CLOSE_PERMISSION_VIEW);
            if (!manageChannel) missing.add(SupportMessageCodes.CLOSE_PERMISSION_CHANNEL);
            if (!managePermissions) missing.add(SupportMessageCodes.CLOSE_PERMISSION_PERMISSIONS);
            return List.copyOf(missing);
        }
    }

    public String line() {
        String line = "ARCHIVE_DIAGNOSTIC phase=" + phase + " exception=" + exceptionClass
                + " discord=" + (discordError == null ? "NONE" : discordError.name());
        if (discordCode != null) line += " discordCode=" + discordCode;
        if (bot != null) line += " view=" + bot.view() + " manageChannel=" + bot.manageChannel()
                + " managePermissions=" + bot.managePermissions() + " admin=" + bot.admin();
        if (nameMatches != null) line += " nameMatches=" + nameMatches;
        if (overridesMatch != null) line += " overridesMatch=" + overridesMatch;
        return line;
    }

    static Failure failure(Phase phase, Exception failure) {
        if (failure instanceof Failure classified) return classified;
        return new Failure(new HolonetArchiveDiagnostic(phase, failure.getClass().getName(),
                failure instanceof ErrorResponseException discord ? discord.getErrorResponse() : null,
                failure instanceof ErrorResponseException discord ? discord.getErrorCode() : null,
                null, null, null));
    }

    static <T> T at(Phase phase, Supplier<T> operation) {
        try { return operation.get(); }
        catch (RuntimeException failure) { throw failure(phase, failure); }
    }

    static final class Failure extends RuntimeException {
        private final HolonetArchiveDiagnostic diagnostic;
        Failure(HolonetArchiveDiagnostic diagnostic) {
            super(diagnostic.line(), null, false, false);
            this.diagnostic = diagnostic;
        }
        HolonetArchiveDiagnostic diagnostic() { return diagnostic; }
    }
}
