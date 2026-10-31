package de.medo.holonetbridge.commons.discord.button;

import de.holonetbridge.commons.message.SupportMessageCodes;
import net.dv8tion.jda.api.components.buttons.Button;
import org.jspecify.annotations.NonNull;
import org.springframework.context.MessageSource;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class HolonetButtonFactory {

    private final MessageSourceAccessor messageSource;

    public HolonetButtonFactory(MessageSource messageSource) {
        this.messageSource = new MessageSourceAccessor(messageSource);
    }

    public @NonNull Button createOpenSupportButton(Locale locale) {
        return Button.success(HolonetOpenSupportButton.BUTTON_ID,
                              messageSource.getMessage(SupportMessageCodes.CHANNEL_CREATE_BUTTON_LABEL, locale));
    }

    public @NonNull Button createReportBugButton(Locale locale) {
        return Button.danger(HolonetReportBugButton.BUTTON_ID,
                             messageSource.getMessage(SupportMessageCodes.REPORT_KIND_BUG_LABEL, locale));
    }

    public @NonNull Button createReportFeatureButton(Locale locale) {
        return Button.success(HolonetReportFeatureButton.BUTTON_ID,
                              messageSource.getMessage(SupportMessageCodes.REPORT_KIND_FEATURE_LABEL, locale));
    }

    public @NonNull Button createAddWatcherButton(Locale locale) {
        return Button.secondary(HolonetAddWatcherButton.BUTTON_ID,
                                messageSource.getMessage(SupportMessageCodes.WATCHER_ADD_BUTTON_LABEL, locale));
    }

    public @NonNull Button createCloseTicketButton(Locale locale) {
        return Button.secondary(HolonetCloseTicketButton.BUTTON_ID,
                                messageSource.getMessage(SupportMessageCodes.CLOSE_BUTTON_LABEL, locale));
    }
}
