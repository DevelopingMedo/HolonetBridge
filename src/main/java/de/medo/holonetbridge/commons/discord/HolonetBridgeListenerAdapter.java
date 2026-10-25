package de.medo.holonetbridge.commons.discord;

import de.medo.holonetbridge.commons.ticket.TicketDto;
import de.medo.holonetbridge.commons.ticket.TicketProvider;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.modals.Modal;
import org.springframework.stereotype.Component;

/**
 * The bridge's single JDA {@link ListenerAdapter}, registered once with the gateway, handling every Discord
 * interaction (slash command, button, modal) for the {@code /ticket} flow.
 *
 * <p>Custom ids are used as a step identifier scheme:
 *
 * <ul>
 *   <li>{@code ticket:open} - the persistent "Support anfragen" button that {@code
 *       TicketChannelConfigurationListenerAdapter} posts via {@code /configure-holonet-bridge}; triggers the
 *       same chooser as {@code /ticket} itself
 *   <li>{@code ticket:start:create|change|cancel} - the three choices offered right after {@code /ticket}
 *   <li>{@code ticket:modal:title} - the modal asking for the ticket title
 *   <li>{@code ticket:type:bug|feature} - the choice between a bug report and a feature request
 *   <li>{@code ticket:modal:description} - the modal collecting the bug/feature description, reused for both
 *       kinds; which template/placeholder it shows depends on the {@link TicketCreationSession#kind()} chosen
 *       beforehand
 * </ul>
 */
@Component
class HolonetBridgeListenerAdapter extends ListenerAdapter {

    static final String COMMAND_NAME = "ticket";
    static final String BUTTON_OPEN = "ticket:open";

    private static final String BUTTON_START_CREATE = "ticket:start:create";
    private static final String BUTTON_START_CHANGE = "ticket:start:change";
    private static final String BUTTON_START_CANCEL = "ticket:start:cancel";

    private static final String MODAL_TITLE = "ticket:modal:title";
    private static final String INPUT_TITLE = "title";

    private static final String BUTTON_TYPE_BUG = "ticket:type:bug";
    private static final String BUTTON_TYPE_FEATURE = "ticket:type:feature";

    private static final String MODAL_DESCRIPTION = "ticket:modal:description";
    private static final String INPUT_DESCRIPTION = "description";

    private static final String BUG_REPORT_TEMPLATE =
            """
            Schritte zum Reproduzieren:
            1.
            2.
            3.

            Erwartetes Verhalten:


            Tatsächliches Verhalten:
            """;

    private static final String FEATURE_PLACEHOLDER =
            "Was soll am Ende möglich sein? Bitte so konkret wie möglich beschreiben.";

    private final TicketCreationSessionStore sessionStore;
    private final TicketProvider ticketProvider;

    HolonetBridgeListenerAdapter(TicketCreationSessionStore sessionStore, TicketProvider ticketProvider) {
        this.sessionStore = sessionStore;
        this.ticketProvider = ticketProvider;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!COMMAND_NAME.equals(event.getName())) {
            return;
        }

        openTicketChooser(event, event.getUser().getIdLong());
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        switch (event.getComponentId()) {
            case BUTTON_OPEN -> openTicketChooser(event, event.getUser().getIdLong());
            case BUTTON_START_CREATE -> event.replyModal(titleModal()).queue();
            case BUTTON_START_CHANGE ->
                event.reply("Das Ändern bestehender Tickets ist noch nicht implementiert.")
                        .setEphemeral(true)
                        .queue();
            case BUTTON_START_CANCEL -> {
                sessionStore.clear(event.getUser().getIdLong());
                event.reply("Abgebrochen.").setEphemeral(true).queue();
            }
            case BUTTON_TYPE_BUG -> {
                sessionStore.get(event.getUser().getIdLong()).ifPresent(session -> session.kind(TicketKind.BUG));
                event.replyModal(descriptionModal(TicketKind.BUG)).queue();
            }
            case BUTTON_TYPE_FEATURE -> {
                sessionStore.get(event.getUser().getIdLong()).ifPresent(session -> session.kind(TicketKind.FEATURE));
                event.replyModal(descriptionModal(TicketKind.FEATURE)).queue();
            }
            default -> {
                // Not one of ours; ignore.
            }
        }
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        switch (event.getModalId()) {
            case MODAL_TITLE -> onTitleSubmitted(event);
            case MODAL_DESCRIPTION -> onDescriptionSubmitted(event);
            default -> {
                // Not one of ours; ignore.
            }
        }
    }

    private void onTitleSubmitted(ModalInteractionEvent event) {
        long userId = event.getUser().getIdLong();
        String title = event.getValue(INPUT_TITLE).getAsString();
        sessionStore.get(userId).ifPresentOrElse(
                session -> {
                    session.title(title);
                    event.reply("Ist es ein Bug oder ein Feature?")
                            .addComponents(ActionRow.of(
                                    Button.primary(BUTTON_TYPE_BUG, "Bug"),
                                    Button.success(BUTTON_TYPE_FEATURE, "Feature")))
                            .setEphemeral(true)
                            .queue();
                },
                () -> event.reply("Die Sitzung ist abgelaufen, bitte starte mit /ticket neu.")
                        .setEphemeral(true)
                        .queue());
    }

    private void onDescriptionSubmitted(ModalInteractionEvent event) {
        long userId = event.getUser().getIdLong();
        String description = event.getValue(INPUT_DESCRIPTION).getAsString();

        sessionStore.get(userId).ifPresentOrElse(
                session -> {
                    String prefixedTitle = titleWithKindPrefix(session);
                    event.deferReply(true).queue();
                    TicketDto ticket = ticketProvider.create(prefixedTitle, description);
                    sessionStore.clear(userId);
                    event.getHook()
                            .sendMessage("Ticket #%s wurde angelegt: %s".formatted(ticket.id(), ticket.title()))
                            .queue();
                },
                () -> event.reply("Die Sitzung ist abgelaufen, bitte starte mit /ticket neu.")
                        .setEphemeral(true)
                        .queue());
    }

    private void openTicketChooser(IReplyCallback interaction, long userId) {
        sessionStore.startOrReset(userId);

        interaction
                .reply("Möchtest du ein Ticket anlegen?")
                .addComponents(ActionRow.of(
                        Button.primary(BUTTON_START_CREATE, "Anlegen"),
                        Button.secondary(BUTTON_START_CHANGE, "Ändern"),
                        Button.danger(BUTTON_START_CANCEL, "Abbruch")))
                .setEphemeral(true)
                .queue();
    }

    private static String titleWithKindPrefix(TicketCreationSession session) {
        String prefix = session.kind() == TicketKind.BUG ? "[Bug] " : "[Feature] ";
        return prefix + session.title();
    }

    private static Modal titleModal() {
        TextInput title = TextInput.create(INPUT_TITLE, TextInputStyle.SHORT)
                .setRequiredRange(1, 100)
                .build();
        return Modal.create(MODAL_TITLE, "Ticket anlegen")
                .addComponents(Label.of("Titel", title))
                .build();
    }

    private static Modal descriptionModal(TicketKind kind) {
        TextInput.Builder descriptionBuilder = TextInput.create(INPUT_DESCRIPTION, TextInputStyle.PARAGRAPH);
        String label;
        if (kind == TicketKind.BUG) {
            descriptionBuilder.setValue(BUG_REPORT_TEMPLATE);
            label = "Schritte zum Reproduzieren";
        } else {
            descriptionBuilder.setPlaceholder(FEATURE_PLACEHOLDER);
            label = "Was wünschst du dir?";
        }

        return Modal.create(MODAL_DESCRIPTION, "Beschreibung")
                .addComponents(Label.of(label, descriptionBuilder.build()))
                .build();
    }
}
