package de.medo.holonetbridge.commons.discord.ticketing;

import java.util.List;

/**
 * Classic-Markdown string helpers for assembling a ticket description. Provider-specific dialects (e.g. Jira) are
 * out of scope for now.
 */
public final class MarkdownFormatterUtil {

    private MarkdownFormatterUtil() {}

    /** A level-2 heading line. */
    public static String h2(String heading) {
        return "## " + heading;
    }

    /** Renders the given Markdown blocks into one document, separated by blank lines. */
    public static String render(List<String> blocks) {
        return String.join("\n\n", blocks);
    }
}
