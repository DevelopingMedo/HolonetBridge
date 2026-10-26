package de.medo.holonetbridge.commons.extapi;

import java.util.Objects;

/**
 * A ticket being created through the bridge, provider-agnostic: carries the title and the description content,
 * which is either a structured bug report (steps, expected, actual, recurrence) or a feature request's free-text
 * description. {@link #getMarkdownFormat()} renders that content as classic Markdown so the {@link TicketProvider}
 * port can stay on plain {@code String} descriptions.
 */
public final class HolonetTicket {

    private final String title;
    private final BugReport bugReport;
    private final String description;

    private HolonetTicket(String title, BugReport bugReport, String description) {
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.bugReport = bugReport;
        this.description = description;
    }

    public static HolonetTicket bug(String title, String steps, String expected, String actual, String recurrence) {
        return new HolonetTicket(title, new BugReport(steps, expected, actual, recurrence), null);
    }

    public static HolonetTicket feature(String title, String description) {
        return new HolonetTicket(title, null, description);
    }

    public String title() {
        return title;
    }

    /** The description as classic Markdown, ready to hand to any provider. */
    public String getMarkdownFormat() {
        return bugReport == null
                ? description
                : MarkdownFormatterUtil.bugReport(
                        bugReport.steps(), bugReport.expected(), bugReport.actual(), bugReport.recurrence());
    }

    private record BugReport(String steps, String expected, String actual, String recurrence) {}
}
