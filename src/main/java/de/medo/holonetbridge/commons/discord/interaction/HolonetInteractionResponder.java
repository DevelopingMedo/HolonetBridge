package de.medo.holonetbridge.commons.discord.interaction;

import de.holonetbridge.commons.message.DiscordMessageCodes;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.springframework.context.MessageSource;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.concurrent.CompletionException;

@Component
@Slf4j
public class HolonetInteractionResponder {

    private final MessageSourceAccessor messageSource;

    public HolonetInteractionResponder(MessageSource messageSource) {
        this.messageSource = new MessageSourceAccessor(messageSource);
    }

    /**
     * Requires a successful reply or deferReply, not deferEdit, which would target the component's message.
     * Visibility is determined by the original reply and cannot be changed here.
     */
    public void updateReplyWithError(InteractionHook hook,
                                     Throwable failure,
                                     String operationKey,
                                     Object... arguments) {
        if (failure instanceof CompletionException && failure.getCause() != null) {
            failure = failure.getCause();
        }
        Locale userLocale = hook.getInteraction().getUserLocale().toLocale();
        String operationMessageForUser = messageSource.getMessage(operationKey, arguments, userLocale);

        String errorMessageForUser = switch (failure) {
            case InsufficientPermissionException permissionException -> {
                log.warn("Discord interaction operation failed [{}]", operationKey, permissionException);
                yield messageSource.getMessage(
                        DiscordMessageCodes.BOT_PERMISSION_MISSING,
                        new Object[]{permissionException.getPermission().getName()},
                        userLocale);
            }
            default -> {
                log.error("Discord interaction operation failed [{}]", operationKey, failure);
                yield messageSource.getMessage(DiscordMessageCodes.UNKNOWN_ERROR, userLocale);
            }
        };

        hook.editOriginal(operationMessageForUser + "\n" + errorMessageForUser).queue();
    }
}
