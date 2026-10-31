package de.medo.holonetbridge.commons.discord.button;

import de.medo.holonetbridge.commons.discord.i18n.I18nProperties;
import de.medo.holonetbridge.commons.discord.i18n.MessageSourceConfiguration;
import de.medo.holonetbridge.commons.discord.listener.HolonetOnButtonInteractionListener;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class HolonetButtonInteractionTest {

    @Test
    void onlyGuildControlsRouteToTheirHandlersAndUnknownIdsAreIgnored() {
        var messages = new MessageSourceConfiguration().messageSource(new I18nProperties(null));
        var factory = new HolonetButtonFactory(messages);
        var open = mock(HolonetOpenSupportButton.class);
        var bug = mock(HolonetReportBugButton.class);
        var feature = mock(HolonetReportFeatureButton.class);
        var watcher = mock(HolonetAddWatcherButton.class);
        var close = mock(HolonetCloseTicketButton.class);
        var listener = new HolonetOnButtonInteractionListener(open, bug, feature, watcher, close);
        var event = mock(ButtonInteractionEvent.class);
        when(event.getComponentId()).thenReturn(
                factory.createOpenSupportButton(Locale.US).getCustomId(),
                factory.createReportBugButton(Locale.US).getCustomId(),
                factory.createReportFeatureButton(Locale.US).getCustomId(),
                factory.createAddWatcherButton(Locale.US).getCustomId(),
                factory.createCloseTicketButton(Locale.US).getCustomId(),
                "unrelated:button");

        listener.onButtonInteraction(event);
        verify(event).isFromGuild();
        verifyNoMoreInteractions(event);
        verifyNoInteractions(open, bug, feature, watcher, close);

        when(event.isFromGuild()).thenReturn(true);
        listener.onButtonInteraction(event);
        verify(open).handle(event);
        listener.onButtonInteraction(event);
        verify(bug).handle(event);
        listener.onButtonInteraction(event);
        verify(feature).handle(event);
        listener.onButtonInteraction(event);
        verify(watcher).handle(event);
        listener.onButtonInteraction(event);
        verify(close).handle(event);
        listener.onButtonInteraction(event);
        verifyNoMoreInteractions(open, bug, feature, watcher, close);
    }

    @Test
    void addWatcherOpensAnEphemeralSingleUserSelectorInTheUserLanguage() {
        var messages = new MessageSourceConfiguration().messageSource(new I18nProperties(null));
        var event = mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);
        when(event.getUserLocale()).thenReturn(DiscordLocale.GERMAN);

        new HolonetAddWatcherButton(messages).handle(event);

        var row = ArgumentCaptor.forClass(ActionRow.class);
        var reply = event.reply("Wähle eine Person aus, die den Kanal sehen und kommentieren darf.");
        verify(reply).addComponents(row.capture());
        verify(reply.addComponents(row.getValue())).setEphemeral(true);
        var menu = (EntitySelectMenu) row.getValue().getComponents().getFirst();
        assertThat(menu.getCustomId()).isEqualTo(HolonetAddWatcherButton.SELECT_ID);
        assertThat(menu.getPlaceholder()).isEqualTo("Person auswählen");
        assertThat(menu.getMinValues()).isEqualTo(1);
        assertThat(menu.getMaxValues()).isEqualTo(1);
        assertThat(menu.getEntityTypes()).containsExactly(EntitySelectMenu.SelectTarget.USER);
    }
}
