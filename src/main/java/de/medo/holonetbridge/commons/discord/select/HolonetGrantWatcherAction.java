package de.medo.holonetbridge.commons.discord.select;

import de.medo.holonetbridge.commons.discord.i18n.Texts;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import org.springframework.stereotype.Component;

/**
 * Handles the watcher user selection ({@code ticket:watcher:select}): grants the chosen member view/send/history/
 * attach permissions on the support channel, or reports when no member could be resolved.
 */
@Component
public class HolonetGrantWatcherAction {

    private final Texts texts;

    public HolonetGrantWatcherAction(Texts texts) {
        this.texts = texts;
    }

    public void handle(EntitySelectInteractionEvent event) {
        Member member = event.getMentions().getMembers().stream().findFirst().orElse(null);
        if (member == null) {
            event.reply(texts.user(event, "watcher.not-found")).setEphemeral(true).queue();
            return;
        }
        grantWatcher(event, member);
    }

    private void grantWatcher(EntitySelectInteractionEvent event, Member member) {
        event.deferReply(true).queue();
        event.getChannel()
                .asTextChannel()
                .upsertPermissionOverride(member)
                .grant(
                        Permission.VIEW_CHANNEL,
                        Permission.MESSAGE_SEND,
                        Permission.MESSAGE_HISTORY,
                        Permission.MESSAGE_ATTACH_FILES)
                .queue(
                        success -> event.getHook()
                                .sendMessage(texts.user(event, "watcher.added", member.getAsMention()))
                                .queue(),
                        failure -> event.getHook()
                                .sendMessage(texts.user(event, "watcher.failed"))
                                .queue());
    }
}
