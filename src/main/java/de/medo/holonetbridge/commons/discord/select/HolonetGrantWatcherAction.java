package de.medo.holonetbridge.commons.discord.select;

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

    public void handle(EntitySelectInteractionEvent event) {
        Member member = event.getMentions().getMembers().stream().findFirst().orElse(null);
        if (member == null) {
            event.reply("Die Person konnte nicht aufgelöst werden.").setEphemeral(true).queue();
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
                                .sendMessage(member.getAsMention() + " wurde als Beobachter hinzugefügt.")
                                .queue(),
                        failure -> event.getHook()
                                .sendMessage("Die Rechte konnten nicht vergeben werden.")
                                .queue());
    }
}
