package de.medo.holonetbridge.commons.discord.listener;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.medo.holonetbridge.commons.discord.command.HolonetSetupCommandAction;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.junit.jupiter.api.Test;

/**
 * Drives {@link HolonetOnSlashCommandInteractionListener}'s dispatch logic only, with {@link
 * HolonetSetupCommandAction} mocked out: that class's own permission checks and modal flow are covered by
 * {@code HolonetSetupCommandActionTest}.
 */
class HolonetOnSlashCommandInteractionListenerTest {

    private final HolonetSetupCommandAction setupCommand = mock(HolonetSetupCommandAction.class);
    private final HolonetOnSlashCommandInteractionListener listener =
            new HolonetOnSlashCommandInteractionListener(setupCommand);

    @Test
    void slashCommandIgnoresOtherCommandNames() {
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        when(event.getName()).thenReturn("not-setup");

        listener.onSlashCommandInteraction(event);

        verify(event, never()).deferReply(anyBoolean());
        verify(event, never()).reply(anyString());
        verify(event, never()).replyModal(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(setupCommand);
    }

    @Test
    void slashCommandForwardsTheSetupCommandToHolonetSetupCommandAction() {
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        when(event.getName()).thenReturn(HolonetSetupCommandAction.COMMAND_SETUP);

        listener.onSlashCommandInteraction(event);

        verify(setupCommand).handle(event);
    }
}
