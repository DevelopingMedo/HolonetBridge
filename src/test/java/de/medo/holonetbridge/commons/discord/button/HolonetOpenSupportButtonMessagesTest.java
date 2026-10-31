package de.medo.holonetbridge.commons.discord.button;

import de.holonetbridge.commons.message.DiscordMessageCodes;
import de.holonetbridge.commons.message.SupportMessageCodes;
import de.medo.holonetbridge.commons.discord.i18n.I18nProperties;
import de.medo.holonetbridge.commons.discord.i18n.MessageSourceConfiguration;
import de.medo.holonetbridge.commons.discord.interaction.HolonetInteractionResponder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.ChannelAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ClassPathResource;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class HolonetOpenSupportButtonMessagesTest {

    private final MessageSource messages = new MessageSourceConfiguration().messageSource(new I18nProperties(null));
    private final HolonetOpenSupportButton handler = new HolonetOpenSupportButton(
            messages, new HolonetInteractionResponder(messages), new HolonetButtonFactory(messages));
    private final ButtonInteractionEvent event = mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);
    private final Guild guild = mock(Guild.class);
    private final InteractionHook hook = mock(InteractionHook.class, RETURNS_DEEP_STUBS);
    private final ReplyCallbackAction reply = mock(ReplyCallbackAction.class, RETURNS_SELF);
    private final ChannelAction<TextChannel> creation = mock(ChannelAction.class, RETURNS_SELF);
    private final TextChannel channel = mock(TextChannel.class);
    private final MessageCreateAction panel = mock(MessageCreateAction.class, RETURNS_SELF);

    @BeforeEach
    void setUp() {
        when(event.getGuild()).thenReturn(guild);
        when(event.getUserLocale()).thenReturn(DiscordLocale.GERMAN);
        when(hook.getInteraction().getUserLocale()).thenReturn(DiscordLocale.GERMAN);
        when(guild.getIdLong()).thenReturn(1L);
        when(guild.getLocale()).thenReturn(DiscordLocale.ENGLISH_US);
        when(guild.createTextChannel(anyString(), any())).thenReturn(creation);
        when(event.reply(anyString())).thenReturn(reply);
        when(channel.getName()).thenReturn("holonetbridge-0000001");
        when(channel.sendMessage(anyString())).thenReturn(panel);
    }

    @Test
    void repliesWithLocalizedProgressBeforeStartingChannelCreation() {
        handler.handle(event);

        verify(event).reply("Dein Support-Kanal wird erstellt …");
        verify(reply).setEphemeral(true);
        verify(event, never()).deferReply(anyBoolean());
        verify(guild, never()).createTextChannel(anyString(), any());

        acknowledge();

        verify(guild).createTextChannel(eq("holonetbridge-0000001"), any());
        verify(hook, never()).editOriginal(anyString());
    }

    @Test
    void failedProgressReplyDoesNotStartChannelCreation() {
        handler.handle(event);
        ArgumentCaptor<Consumer<Throwable>> failure = ArgumentCaptor.forClass(Consumer.class);
        verify(reply).queue(any(), failure.capture());

        failure.getValue().accept(new IllegalStateException("Test acknowledgement failure"));

        verify(guild, never()).createTextChannel(anyString(), any());
        verify(hook, never()).editOriginal(anyString());
    }

    @ParameterizedTest
    @EnumSource(value = DiscordLocale.class, names = {"ENGLISH_US", "GERMAN", "CROATIAN", "RUSSIAN"})
    void panelUsesGuildLanguageAndSuccessEditsTheUserReply(DiscordLocale guildLocale) {
        when(guild.getLocale()).thenReturn(guildLocale);
        createChannel();

        Locale locale = guildLocale.toLocale();
        verify(channel).sendMessage(text(SupportMessageCodes.REPORT_TYPE_SELECTION_MESSAGE, locale));
        var firstRow = ArgumentCaptor.forClass(ActionRow.class);
        var secondRow = ArgumentCaptor.forClass(ActionRow.class);
        verify(panel).addComponents(firstRow.capture(), secondRow.capture());
        assertThat(firstRow.getValue().getButtons()).extracting(Button::getCustomId)
                .containsExactly(HolonetReportBugButton.BUTTON_ID, HolonetReportFeatureButton.BUTTON_ID);
        assertThat(firstRow.getValue().getButtons()).extracting(Button::getLabel)
                .containsExactly(text(SupportMessageCodes.REPORT_KIND_BUG_LABEL, locale),
                        text(SupportMessageCodes.REPORT_KIND_FEATURE_LABEL, locale));
        assertThat(secondRow.getValue().getButtons()).extracting(Button::getCustomId)
                .containsExactly(HolonetAddWatcherButton.BUTTON_ID, HolonetCloseTicketButton.BUTTON_ID);
        assertThat(secondRow.getValue().getButtons()).extracting(Button::getLabel)
                .containsExactly(text(SupportMessageCodes.WATCHER_ADD_BUTTON_LABEL, locale),
                        text(SupportMessageCodes.CLOSE_BUTTON_LABEL, locale));
        verify(hook, never()).editOriginal(anyString());

        ArgumentCaptor<Consumer<Message>> success = ArgumentCaptor.forClass(Consumer.class);
        verify(panel).queue(success.capture(), any());
        success.getValue().accept(mock(Message.class));

        verify(hook).editOriginal("Dein Support-Kanal wurde erstellt: holonetbridge-0000001");
        verify(hook.editOriginal("Dein Support-Kanal wurde erstellt: holonetbridge-0000001")).queue();
    }

    @ParameterizedTest
    @EnumSource(value = DiscordLocale.class, names = {"ENGLISH_US", "GERMAN", "CROATIAN", "RUSSIAN"})
    void creationFailureUsesUserLanguageWithoutExposingExceptionText(DiscordLocale userLocale) {
        when(event.getUserLocale()).thenReturn(userLocale);
        when(hook.getInteraction().getUserLocale()).thenReturn(userLocale);
        handler.handle(event);
        verify(event).reply(text(SupportMessageCodes.CHANNEL_CREATE_IN_PROGRESS, userLocale.toLocale()));
        acknowledge();
        ArgumentCaptor<Consumer<Throwable>> failure = ArgumentCaptor.forClass(Consumer.class);
        verify(creation).queue(any(), failure.capture());

        failure.getValue().accept(new IllegalStateException("Internal test diagnostic"));

        var content = ArgumentCaptor.forClass(String.class);
        verify(hook).editOriginal(content.capture());
        assertThat(content.getValue()).isEqualTo(
                text(SupportMessageCodes.CHANNEL_CREATE_FAILURE, userLocale.toLocale()) + "\n"
                        + text(DiscordMessageCodes.UNKNOWN_ERROR, userLocale.toLocale()))
                .doesNotContain("Internal test diagnostic", "{0}");
        verify(channel, never()).sendMessage(anyString());
    }

    @Test
    void permissionFailureFormatsTheMissingBotPermission() {
        handler.handle(event);
        acknowledge();
        ArgumentCaptor<Consumer<Throwable>> failure = ArgumentCaptor.forClass(Consumer.class);
        verify(creation).queue(any(), failure.capture());
        var permissionFailure = mock(InsufficientPermissionException.class);
        when(permissionFailure.getPermission()).thenReturn(Permission.MANAGE_CHANNEL);

        failure.getValue().accept(permissionFailure);

        verify(hook).editOriginal(text(SupportMessageCodes.CHANNEL_CREATE_FAILURE, Locale.GERMAN) + "\n"
                + text(DiscordMessageCodes.BOT_PERMISSION_MISSING, Locale.GERMAN,
                        Permission.MANAGE_CHANNEL.getName()));
    }

    @Test
    void panelFailureIdentifiesTheExistingChannelInsteadOfReportingCreationFailure() {
        createChannel();
        ArgumentCaptor<Consumer<Throwable>> failure = ArgumentCaptor.forClass(Consumer.class);
        verify(panel).queue(any(), failure.capture());

        failure.getValue().accept(new IllegalStateException("Internal test diagnostic"));

        var content = ArgumentCaptor.forClass(String.class);
        verify(hook).editOriginal(content.capture());
        assertThat(content.getValue()).isEqualTo(
                text(SupportMessageCodes.CHANNEL_CREATE_INCOMPLETE, Locale.GERMAN, channel.getName()) + "\n"
                        + text(DiscordMessageCodes.UNKNOWN_ERROR, Locale.GERMAN))
                .contains(channel.getName()).doesNotContain("Internal test diagnostic", "{0}", "registriert");
        verify(guild, times(1)).createTextChannel(anyString(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "_de", "_hr", "_ru"})
    void bothCodeCatalogsHaveDistinctNonemptyTranslationsInEveryBundle(String suffix) throws Exception {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("i18n/messages" + suffix + ".yml"));
        var catalog = yaml.getObject();
        var codes = new HashSet<String>();

        for (var codeClass : List.of(SupportMessageCodes.class, DiscordMessageCodes.class)) {
            String prefix = codeClass == SupportMessageCodes.class
                    ? "holonet.discord.support." : "holonet.discord.error.";
            for (var field : codeClass.getFields()) {
                String code = (String) field.get(null);
                assertThat(code).startsWith(prefix).matches("holonet\\.discord\\.[a-z]+(?:[.-][a-z]+)*");
                assertThat(codes.add(code)).as("Unique message code: %s", code).isTrue();
                assertThat(catalog.getProperty(code)).as("messages%s.yml: %s", suffix, code).isNotBlank();
            }
        }
    }

    private void acknowledge() {
        ArgumentCaptor<Consumer<InteractionHook>> success = ArgumentCaptor.forClass(Consumer.class);
        verify(reply).queue(success.capture(), any());
        success.getValue().accept(hook);
    }

    private void createChannel() {
        handler.handle(event);
        acknowledge();
        ArgumentCaptor<Consumer<TextChannel>> success = ArgumentCaptor.forClass(Consumer.class);
        verify(creation).queue(success.capture(), any());
        success.getValue().accept(channel);
    }

    private String text(String code, Locale locale, Object... arguments) {
        return messages.getMessage(code, arguments, locale);
    }
}
