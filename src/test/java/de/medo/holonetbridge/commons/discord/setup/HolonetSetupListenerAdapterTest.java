package de.medo.holonetbridge.commons.discord.setup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.medo.holonetbridge.commons.discord.DiscordGatewayConfiguration;
import java.util.function.Consumer;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;

/**
 * Drives {@link HolonetSetupListenerAdapter} using Mockito mocks of the JDA event/entity types,
 * the same way {@code HolonetTicketModalDialogListenerAdapterTest} does, without any real gateway connection
 * or database.
 */
class HolonetSetupListenerAdapterTest {

    private static final long GUILD_ID = 999L;
    private static final long CHANNEL_ID = 111L;
    private static final long MESSAGE_ID = 222L;

    private final HolonetDiscordServerRepository repository = mock(HolonetDiscordServerRepository.class);
    private final HolonetSetupListenerAdapter listener =
            new HolonetSetupListenerAdapter(repository);

    @Test
    void slashCommandIgnoresOtherCommandNames() {
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        when(event.getName()).thenReturn("not-setup");

        listener.onSlashCommandInteraction(event);

        verify(event, never()).deferReply(anyBoolean());
        verifyNoInteractions(repository);
    }

    @Test
    void slashCommandPostsTheSupportButtonIntoTheChosenChannelAndPersistsTheConfiguration() {
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        when(event.getName()).thenReturn(DiscordGatewayConfiguration.COMMAND_SETUP);

        Guild guild = mock(Guild.class);
        when(guild.getIdLong()).thenReturn(GUILD_ID);
        when(event.getGuild()).thenReturn(guild);

        TextChannel channel = mock(TextChannel.class);
        when(channel.getIdLong()).thenReturn(CHANNEL_ID);
        when(channel.getAsMention()).thenReturn("#test");
        GuildChannelUnion channelUnion = mock(GuildChannelUnion.class);
        when(channelUnion.asTextChannel()).thenReturn(channel);
        OptionMapping channelOption = mock(OptionMapping.class);
        when(channelOption.getAsChannel()).thenReturn(channelUnion);
        when(event.getOption(DiscordGatewayConfiguration.OPTION_CHANNEL)).thenReturn(channelOption);

        ReplyCallbackAction deferAction = mock(ReplyCallbackAction.class, Answers.RETURNS_SELF);
        when(event.deferReply(true)).thenReturn(deferAction);

        MessageCreateAction sendAction = mock(MessageCreateAction.class, Answers.RETURNS_SELF);
        when(channel.sendMessage(anyString())).thenReturn(sendAction);

        InteractionHook hook = mock(InteractionHook.class);
        when(event.getHook()).thenReturn(hook);
        WebhookMessageCreateAction<Message> hookSendAction = newHookSendAction();
        when(hook.sendMessage(anyString())).thenReturn(hookSendAction);

        listener.onSlashCommandInteraction(event);

        verify(deferAction).queue();
        verify(channel).sendMessage("Brauchst du Unterstützung? Klick unten, um ein Support-Ticket zu erstellen.");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<Message>> successCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(sendAction).queue(successCaptor.capture());

        Message message = mock(Message.class);
        when(message.getIdLong()).thenReturn(MESSAGE_ID);
        successCaptor.getValue().accept(message);

        ArgumentCaptor<HolonetDiscordServer> configurationCaptor =
                ArgumentCaptor.forClass(HolonetDiscordServer.class);
        verify(repository).save(configurationCaptor.capture());
        assertThat(configurationCaptor.getValue().guildId()).isEqualTo(GUILD_ID);
        assertThat(configurationCaptor.getValue().channelId()).isEqualTo(CHANNEL_ID);
        assertThat(configurationCaptor.getValue().messageId()).isEqualTo(MESSAGE_ID);

        verify(hook).sendMessage("Eingerichtet: Die Support-Nachricht steht jetzt in #test.");
        verify(hookSendAction).queue();
    }

    /**
     * {@code WebhookMessageCreateAction<T>} erases to a raw type via {@code .class}, so a single, documented
     * unchecked cast lives here instead of being repeated (and unsuppressed) at every call site.
     */
    @SuppressWarnings("unchecked")
    private static WebhookMessageCreateAction<Message> newHookSendAction() {
        return mock(WebhookMessageCreateAction.class, Answers.RETURNS_SELF);
    }
}
