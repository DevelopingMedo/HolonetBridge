package de.medo.holonetbridge.commons.extapi;

/**
 * Renders ticket descriptions as classic Markdown, independent of any provider. For now the bridge only specs
 * classic Markdown (Gitea renders it as GFM); provider-specific dialects (e.g. Jira) are deliberately out of scope
 * here.
 */
public final class MarkdownFormatterUtil {

    private MarkdownFormatterUtil() {}

    /** Renders the four bug-report fields as a classic-Markdown description. */
    public static String bugReport(String steps, String expected, String actual, String recurrence) {
        return String.join("\n\n",
                section("Was hast du vor dem Fehler gemacht?", steps),
                section("Was hättest du erwartet?", expected),
                section("Was ist stattdessen passiert?", actual),
                section("Tritt der Fehler erneut auf?", recurrence));
    }

    private static String section(String label, String body) {
        return "**" + label + "**\n" + body;
    }
}
