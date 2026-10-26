package de.medo.holonetbridge.commons.discord.button;

import de.medo.holonetbridge.commons.discord.ticketing.HolonetSupportChannel;
import de.medo.holonetbridge.commons.discord.ticketing.HolonetSupportChannelRepository;
import de.medo.holonetbridge.commons.discord.ticketing.HolonetTicketCreationSessionStore;
import java.util.EnumSet;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.springframework.stereotype.Component;

/**
 * Handles the persistent "Support anfragen" button (custom id {@code ticket:open}). It creates a
 * dedicated, private text channel for the requesting user, remembers it in {@link HolonetSupportChannelRepository},
 * starts the user's {@link HolonetTicketCreationSessionStore} session, and posts the bug/idea chooser into the
 * new channel.
 */
@Component
public class HolonetOpenSupportButton {

    /** Custom id of the persistent "Support anfragen" button. */
    public static final String BUTTON_ID = "ticket:open";

    private final HolonetTicketCreationSessionStore sessionStore;
    private final HolonetSupportChannelRepository supportChannelRepository;

    public HolonetOpenSupportButton(
            HolonetTicketCreationSessionStore sessionStore,
            HolonetSupportChannelRepository supportChannelRepository) {
        this.sessionStore = sessionStore;
        this.supportChannelRepository = supportChannelRepository;
    }

    public void handle(ButtonInteractionEvent event) {
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null) {
            event.reply("Support-Kanäle können nur innerhalb eines Servers erstellt werden.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        long guildId = guild.getIdLong();
        long userId = member.getIdLong();
        long number = supportChannelRepository
                .findTopByGuildIdOrderByNumberDesc(guildId)
                .map(channel -> channel.number() + 1)
                .orElse(1L);
        String name = "holonetbridge-%05d".formatted(number);
        Category parent = event.getChannel().asTextChannel().getParentCategory();

        event.deferReply(true).queue();

        guild.createTextChannel(name, parent)
                .addPermissionOverride(guild.getPublicRole(), null, EnumSet.of(Permission.VIEW_CHANNEL))
                .addPermissionOverride(
                        member,
                        EnumSet.of(
                                Permission.VIEW_CHANNEL,
                                Permission.MESSAGE_SEND,
                                Permission.MESSAGE_HISTORY,
                                Permission.MESSAGE_ATTACH_FILES),
                        null)
                .addPermissionOverride(
                        guild.getSelfMember(),
                        EnumSet.of(
                                Permission.VIEW_CHANNEL,
                                Permission.MESSAGE_SEND,
                                Permission.MANAGE_CHANNEL,
                                Permission.MESSAGE_HISTORY),
                        null)
                .queue(channel -> {
                    supportChannelRepository.save(
                            new HolonetSupportChannel(channel.getIdLong(), guildId, userId, number));
                    sessionStore.startOrReset(userId);
                    channel.sendMessage("Hast du ein Bug gefunden oder hast du eine neue Idee?")
                            .addComponents(
                                    ActionRow.of(
                                            Button.primary(HolonetBugReportModalButton.BUTTON_ID, "Bug"),
                                            Button.success(HolonetFeatureModalButton.BUTTON_ID, "Idee")),
                                    ActionRow.of(
                                            Button.secondary(HolonetAddWatcherButton.BUTTON_ID, "Beobachter hinzufügen")))
                            .queue();
                    event.getHook()
                            .sendMessage("Support-Kanal erstellt: " + channel.getAsMention())
                            .queue();
                }, failure -> event.getHook()
                        .sendMessage("Der Support-Kanal konnte nicht erstellt werden.")
                        .queue());
    }
}
