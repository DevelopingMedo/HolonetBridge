package de.medo.holonetbridge.commons.discord.modal;

import de.medo.holonetbridge.commons.domain.HolonetReportFormData;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.modals.Modal;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;


@Component
public class HolonetTicketReportModalFactory {

    public static final String MODAL_ID_BUG_REPORT = "ticket:modal:bug-report";
    public static final String MODAL_ID_FEATURE_REPORT = "ticket:modal:feature";

    public static final String INPUT_TITLE = "title";
    public static final String INPUT_DESCRIPTION = "description";
    public static final String INPUT_STEPS = "steps";
    public static final String INPUT_EXPECTED = "expected";
    public static final String INPUT_ACTUAL = "actual";
    public static final String INPUT_RECURRENCE = "recurrence";
    private final MessageSource messageSource;

    public HolonetTicketReportModalFactory(MessageSource messageSource){
        this.messageSource = messageSource;
    }


    public Modal createBugReportModal(HolonetReportFormData data, Locale locale) {
        TextInput title = reportInput(INPUT_TITLE, TextInputStyle.SHORT, data.getTitle());
        TextInput steps = reportInput(INPUT_STEPS, TextInputStyle.PARAGRAPH, data.getSteps());
        TextInput expected = reportInput(INPUT_EXPECTED, TextInputStyle.PARAGRAPH, data.getExpected());
        TextInput actual = reportInput(INPUT_ACTUAL, TextInputStyle.PARAGRAPH, data.getActual());
        var recurrence = StringSelectMenu.create(INPUT_RECURRENCE)
                .addOption(messageSource.getMessage("bug.recurrence-always", null, locale), "always")
                .addOption(messageSource.getMessage("bug.recurrence-sometimes", null, locale), "sometimes")
                .addOption(messageSource.getMessage("bug.recurrence-once", null, locale), "once")
                .addOption(messageSource.getMessage("bug.recurrence-not-retried", null, locale), "not-retried")
                .setRequired(true);
        if (data.getRecurrence() != null) {
            recurrence.setDefaultValues(normalizeRecurrence(data.getRecurrence()));
        }
        return Modal.create(MODAL_ID_BUG_REPORT, messageSource.getMessage("bug.modal.title", null, locale))
                .addComponents(
                        Label.of(messageSource.getMessage("ticket.title", null, locale), messageSource.getMessage("bug.title-hint", null, locale), title),
                        Label.of(messageSource.getMessage("bug.steps", null, locale), messageSource.getMessage("bug.steps-hint", null, locale), steps),
                        Label.of(messageSource.getMessage("bug.expected", null, locale), messageSource.getMessage("bug.expected-hint", null, locale), expected),
                        Label.of(messageSource.getMessage("bug.actual", null, locale), messageSource.getMessage("bug.actual-hint", null, locale), actual),
                        Label.of(messageSource.getMessage("bug.recurrence", null, locale), recurrence.build()))
                .build();
    }


    public Modal createFeatureModal(HolonetReportFormData data, Locale locale) {
        return Modal.create(MODAL_ID_FEATURE_REPORT, messageSource.getMessage("feature.modal.title", null, locale))
                .addComponents(
                        Label.of(messageSource.getMessage("ticket.title", null, locale), messageSource.getMessage("feature.title-hint", null, locale),
                                reportInput(INPUT_TITLE, TextInputStyle.SHORT, data.getTitle())),
                        Label.of(messageSource.getMessage("ticket.description", null, locale), messageSource.getMessage("feature.description-hint", null, locale),
                                reportInput(INPUT_DESCRIPTION, TextInputStyle.PARAGRAPH, data.getDescription())))
                .build();
    }

    public static String normalizeRecurrence(String recurrence) {
        // Modals opened before stable option values were introduced can still submit German labels.
        return switch (recurrence) {
            case "Jedes Mal" -> "always";
            case "Manchmal" -> "sometimes";
            case "Bisher nur einmal" -> "once";
            case "Noch nicht erneut ausprobiert" -> "not-retried";
            default -> recurrence;
        };
    }

    private TextInput reportInput(String id, TextInputStyle style, String value) {
        return TextInput.create(id, style)
                .setRequiredRange(1, style == TextInputStyle.SHORT ? 100 : 4000)
                .setValue(value)
                .build();
    }

}
