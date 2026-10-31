package de.medo.holonetbridge.commons.discord.ticketing;

import de.medo.holonetbridge.commons.discord.interaction.HolonetInteractionResponder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import de.medo.holonetbridge.commons.discord.button.*;
import de.medo.holonetbridge.commons.discord.i18n.I18nProperties;
import de.medo.holonetbridge.commons.discord.i18n.MessageSourceConfiguration;
import de.medo.holonetbridge.commons.discord.modal.HolonetTicketReportModalFactory;
import de.medo.holonetbridge.commons.discord.modal.HolonetTicketSubmitter;
import de.medo.holonetbridge.commons.discord.modal.interaction.*;
import de.medo.holonetbridge.commons.discord.select.HolonetGrantWatcherAction;
import de.medo.holonetbridge.commons.service.HolonetBotInteractionSessionService;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HolonetTicketInteractionGuardTest {
    private final org.springframework.context.MessageSource messages =
            new MessageSourceConfiguration().messageSource(new I18nProperties(null));
    private final HolonetTicketInteractionGuard guard = new HolonetTicketInteractionGuard(messages);

    @ParameterizedTest
    @CsvSource({"true,true,true", "true,false,false", "false,true,false", "false,false,false"})
    void anyMemberNeedsBothEffectiveChannelPermissions(boolean view, boolean send, boolean allowed) {
        var event = event(ButtonInteractionEvent.class);
        when(event.getMember().hasPermission(eq(event.getGuildChannel()), any(Permission[].class)))
                .thenAnswer(call -> java.util.Arrays.stream((Permission[]) call.getRawArguments()[1])
                        .allMatch(permission -> switch (permission) {
                            case VIEW_CHANNEL -> view;
                            case MESSAGE_SEND -> send;
                            default -> false;
                        }));
        assertThat(guard.allowCreate(event)).isEqualTo(allowed);
        assertThat(guard.allowWatcher(event)).isEqualTo(allowed);
    }

    @Test
    void rejectsThreadsAndMissingMembersWithoutCastingInvalidChannels() {
        var event = event(ButtonInteractionEvent.class);
        when(event.getChannel().getType()).thenReturn(ChannelType.GUILD_PUBLIC_THREAD);
        assertThat(guard.allowCreate(event)).isFalse();
        assertThat(guard.allowWatcher(event)).isFalse();
        verify(event, never()).getGuildChannel();
        when(event.getChannel().getType()).thenReturn(ChannelType.TEXT);
        when(event.getMember()).thenReturn(null);
        assertThat(guard.allowCreate(event)).isFalse();
    }

    @Test
    void readOnlyMembersCannotOpenOrSubmitReportsOrGrantWatchers() {
        var data = mock(HolonetBotInteractionSessionService.class);
        var submitter = mock(HolonetTicketSubmitter.class);
        var factory = mock(HolonetTicketReportModalFactory.class);
        var button = event(ButtonInteractionEvent.class);
        new HolonetReportBugButton(messages, factory, data, guard).handle(button);
        new HolonetReportFeatureButton(messages, factory, data, guard).handle(button);
        new HolonetAddWatcherButton(messages, guard).handle(button);
        var modal = event(ModalInteractionEvent.class);
        new HolonetBugReportSubmitModalAction(submitter, messages, data, guard).handle(modal);
        new HolonetFeatureSubmittedModalAction(submitter, data, messages, guard).handle(modal);
        var watcher = event(EntitySelectInteractionEvent.class);
        new HolonetGrantWatcherAction(messages, new HolonetInteractionResponder(messages), new HolonetChannelCoordinator(), guard).handle(watcher);

        verifyNoInteractions(data, submitter, factory);
        verify(button, never()).replyModal(any());
        verify(watcher, never()).getMentions();
        verify(watcher, never()).deferReply(anyBoolean());
        verify(modal, never()).getValue(anyString());
    }

    private <T extends IReplyCallback> T event(Class<T> type) {
        var event = mock(type, RETURNS_DEEP_STUBS);
        when(event.getGuild().getIdLong()).thenReturn(1L);
        when(event.getUser().getIdLong()).thenReturn(2L);
        when(event.getChannel().getIdLong()).thenReturn(3L);
        when(event.getChannel().getType()).thenReturn(ChannelType.TEXT);
        when(event.getUserLocale()).thenReturn(DiscordLocale.ENGLISH_US);
        return event;
    }
}
