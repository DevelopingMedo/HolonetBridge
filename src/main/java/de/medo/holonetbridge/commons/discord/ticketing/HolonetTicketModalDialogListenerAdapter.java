package de.medo.holonetbridge.commons.discord.ticketing;

import de.medo.holonetbridge.commons.ticket.TicketDto;
import de.medo.holonetbridge.commons.ticket.TicketProvider;
import java.util.EnumSet;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.modals.Modal;
import org.springframework.stereotype.Component;

/**
 * The bridge's single JDA {@link ListenerAdapter}, registered once with the gateway, handling every Discord
 * interaction (button, modal) for the ticket flow.
 *
 * <p>Clicking the persistent "Support anfragen" button creates a dedicated, private text channel and posts the
 * bug/idea chooser there; the rest of the conversation (kind choice, modal, confirmation) stays in that
 * channel. An idea collects a free-form title and description; a bug collects a title plus a four-field report
 * (steps, expected result, actual result, recurrence).
 *
 * <p>The Discord-visible wording uses "Idee", while the backend keeps "Feature" (see {@link HolonetTicketKind}
 * and the {@code [Feature]} title prefix). Internal ids and constant names therefore use {@code feature} even
 * though the user sees "Idee".
 *
 * <p>Custom ids are used as a step identifier scheme:
 *
 * <ul>
 *   <li>{@code ticket:open} - the persistent "Support anfragen" button that {@code
 *       HolonetSetupListenerAdapter} posts via {@code /setup}; creates the
 *       support channel and posts the kind chooser
 *   <li>{@code ticket:kind:bug|feature} - the bug/idea choice in the support channel
 *   <li>{@code ticket:modal:bug-report} - the bug report modal (title and four report fields)
 *   <li>{@code ticket:modal:feature} - the idea modal collecting title and description
 * </ul>
 */
@Component
public class HolonetTicketModalDialogListenerAdapter extends ListenerAdapter {

    public static final String BUTTON_OPEN = "ticket:open";

    private static final String BUTTON_KIND_BUG = "ticket:kind:bug";
    private static final String BUTTON_KIND_FEATURE = "ticket:kind:feature";
    private static final String BUTTON_ADD_WATCHER = "ticket:watcher:add";
    private static final String SELECT_WATCHER = "ticket:watcher:select";

    private static final String MODAL_FEATURE = "ticket:modal:feature";
    private static final String MODAL_BUG_REPORT = "ticket:modal:bug-report";

    private static final String INPUT_TITLE = "title";
    private static final String INPUT_DESCRIPTION = "description";
    private static final String INPUT_STEPS = "steps";
    private static final String INPUT_EXPECTED = "expected";
    private static final String INPUT_ACTUAL = "actual";
    private static final String INPUT_RECURRENCE = "recurrence";

    private static final String BUG_TITLE_HINT = "Beschreibe das Problem in einem kurzen Satz.";
    private static final String FEATURE_TITLE_HINT = "Beschreibe deine Idee in einem kurzen Satz.";
    private static final String FEATURE_DESCRIPTION_HINT =
            "Was soll am Ende möglich sein? Bitte so konkret wie möglich beschreiben.";
    private static final String BUG_STEPS_HINT =
            "Schritte der Reihe nach, damit jemand sie nachmachen kann. Inkl. Voraussetzungen (Einstellungen).";
    private static final String BUG_EXPECTED_HINT = "Was sollte nach diesen Schritten normalerweise passieren?";
    private static final String BUG_ACTUAL_HINT =
            "Beschreibe das Ergebnis. Füge wenn möglich die exakte und vollständige Fehlermeldung hier ein.";

    private final HolonetTicketCreationSessionStore sessionStore;
    private final TicketProvider ticketProvider;
    private final HolonetSupportChannelRepository supportChannelRepository;

    HolonetTicketModalDialogListenerAdapter(
            HolonetTicketCreationSessionStore sessionStore,
            TicketProvider ticketProvider,
            HolonetSupportChannelRepository supportChannelRepository) {
        this.sessionStore = sessionStore;
        this.ticketProvider = ticketProvider;
        this.supportChannelRepository = supportChannelRepository;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        switch (event.getComponentId()) {
            case BUTTON_OPEN -> openSupportChannel(event);
            case BUTTON_KIND_BUG -> {
                sessionStore
                        .get(event.getUser().getIdLong())
                        .ifPresent(session -> session.kind(HolonetTicketKind.BUG));
                event.replyModal(bugReportModal()).queue();
            }
            case BUTTON_KIND_FEATURE -> {
                sessionStore
                        .get(event.getUser().getIdLong())
                        .ifPresent(session -> session.kind(HolonetTicketKind.FEATURE));
                event.replyModal(featureModal()).queue();
            }
            case BUTTON_ADD_WATCHER -> openWatcherSelector(event);
            default -> {
                // Not one of ours; ignore.
            }
        }
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        switch (event.getModalId()) {
            case MODAL_FEATURE -> onFeatureSubmitted(event);
            case MODAL_BUG_REPORT -> onBugReportSubmitted(event);
            default -> {
                // Not one of ours; ignore.
            }
        }
    }

    @Override
    public void onEntitySelectInteraction(EntitySelectInteractionEvent event) {
        if (!SELECT_WATCHER.equals(event.getComponentId())) {
            return;
        }

        Member member = event.getMentions().getMembers().stream().findFirst().orElse(null);
        if (member == null) {
            event.reply("Die Person konnte nicht aufgelöst werden.").setEphemeral(true).queue();
            return;
        }
        grantWatcher(event, member);
    }

    private void openWatcherSelector(ButtonInteractionEvent event) {
        EntitySelectMenu menu = EntitySelectMenu.create(SELECT_WATCHER, EntitySelectMenu.SelectTarget.USER)
                .setRequiredRange(1, 1)
                .setPlaceholder("Person auswählen")
                .build();
        event.reply("Wähle eine Person aus, die den Kanal sehen und kommentieren darf.")
                .addComponents(ActionRow.of(menu))
                .setEphemeral(true)
                .queue();
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

    private void openSupportChannel(ButtonInteractionEvent event) {
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
                                            Button.primary(BUTTON_KIND_BUG, "Bug"),
                                            Button.success(BUTTON_KIND_FEATURE, "Idee")),
                                    ActionRow.of(
                                            Button.secondary(BUTTON_ADD_WATCHER, "Beobachter hinzufügen")))
                            .queue();
                    event.getHook()
                            .sendMessage("Support-Kanal erstellt: " + channel.getAsMention())
                            .queue();
                }, failure -> event.getHook()
                        .sendMessage("Der Support-Kanal konnte nicht erstellt werden.")
                        .queue());
    }

    private void onFeatureSubmitted(ModalInteractionEvent event) {
        long userId = event.getUser().getIdLong();
        String title = event.getValue(INPUT_TITLE).getAsString();
        String description = event.getValue(INPUT_DESCRIPTION).getAsString();

        sessionStore.get(userId).ifPresentOrElse(
                session -> createTicket(
                        event, userId, titleWithKindPrefix(session.kind(), title), description),
                () -> replySessionExpired(event));
    }

    private void onBugReportSubmitted(ModalInteractionEvent event) {
        long userId = event.getUser().getIdLong();
        String title = event.getValue(INPUT_TITLE).getAsString();
        String description = assembleBugDescription(
                event.getValue(INPUT_STEPS).getAsString(),
                event.getValue(INPUT_EXPECTED).getAsString(),
                event.getValue(INPUT_ACTUAL).getAsString(),
                event.getValue(INPUT_RECURRENCE).getAsStringList().get(0));

        sessionStore.get(userId).ifPresentOrElse(
                session -> createTicket(
                        event, userId, titleWithKindPrefix(session.kind(), title), description),
                () -> replySessionExpired(event));
    }

    private void createTicket(ModalInteractionEvent event, long userId, String title, String description) {
        event.deferReply(true).queue();
        TicketDto ticket = ticketProvider.create(title, description);
        long channelId = event.getChannel().getIdLong();
        supportChannelRepository.findById(channelId).ifPresent(channel -> {
            channel.ticketId(ticket.id());
            supportChannelRepository.save(channel);
        });
        sessionStore.clear(userId);
        event.getChannel()
                .asTextChannel()
                .sendMessage("Ticket #%s wurde angelegt: %s".formatted(ticket.id(), ticket.title()))
                .queue();
    }

    private static void replySessionExpired(ModalInteractionEvent event) {
        event.reply("Die Sitzung ist abgelaufen, bitte klick erneut auf „Support anfragen“.")
                .setEphemeral(true)
                .queue();
    }

    private static String titleWithKindPrefix(HolonetTicketKind kind, String title) {
        String prefix = kind == HolonetTicketKind.BUG ? "[Bug] " : "[Feature] ";
        return prefix + title;
    }

    private static String assembleBugDescription(String steps, String expected, String actual, String recurrence) {
        return new StringBuilder()
                .append("**Was hast du vor dem Fehler gemacht?**").append('\n')
                .append(steps).append('\n')
                .append('\n')
                .append("**Was hättest du erwartet?**").append('\n')
                .append(expected).append('\n')
                .append('\n')
                .append("**Was ist stattdessen passiert?**").append('\n')
                .append(actual).append('\n')
                .append('\n')
                .append("**Tritt der Fehler erneut auf?**").append('\n')
                .append(recurrence).append('\n')
                .toString();
    }

    private static Modal featureModal() {
        TextInput title = TextInput.create(INPUT_TITLE, TextInputStyle.SHORT)
                .setRequiredRange(1, 100)
                .build();
        TextInput description = TextInput.create(INPUT_DESCRIPTION, TextInputStyle.PARAGRAPH)
                .build();
        return Modal.create(MODAL_FEATURE, "Neue Idee")
                .addComponents(
                        Label.of("Titel", FEATURE_TITLE_HINT, title),
                        Label.of("Beschreibung", FEATURE_DESCRIPTION_HINT, description))
                .build();
    }

    private static Modal bugReportModal() {
        TextInput title = TextInput.create(INPUT_TITLE, TextInputStyle.SHORT)
                .setRequiredRange(1, 100)
                .build();
        TextInput steps = TextInput.create(INPUT_STEPS, TextInputStyle.PARAGRAPH).build();
        TextInput expected = TextInput.create(INPUT_EXPECTED, TextInputStyle.PARAGRAPH).build();
        TextInput actual = TextInput.create(INPUT_ACTUAL, TextInputStyle.PARAGRAPH).build();
        StringSelectMenu recurrence = StringSelectMenu.create(INPUT_RECURRENCE)
                .addOption("Jedes Mal", "Jedes Mal")
                .addOption("Manchmal", "Manchmal")
                .addOption("Bisher nur einmal", "Bisher nur einmal")
                .addOption("Noch nicht erneut ausprobiert", "Noch nicht erneut ausprobiert")
                .setRequired(true)
                .build();
        return Modal.create(MODAL_BUG_REPORT, "Bug melden")
                .addComponents(
                        Label.of("Titel", BUG_TITLE_HINT, title),
                        Label.of("Was hast du vor dem Fehler gemacht?", BUG_STEPS_HINT, steps),
                        Label.of("Was hättest du erwartet?", BUG_EXPECTED_HINT, expected),
                        Label.of("Was ist stattdessen passiert?", BUG_ACTUAL_HINT, actual),
                        Label.of("Tritt der Fehler erneut auf?", recurrence))
                .build();
    }
}
