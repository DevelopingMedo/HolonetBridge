package de.medo.holonetbridge.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import de.medo.holonetbridge.HolonetBridgeApplication;
import de.medo.holonetbridge.commons.crypto.EncryptedSecret;
import de.medo.holonetbridge.commons.discord.button.HolonetFeatureModalButton;
import de.medo.holonetbridge.commons.discord.modal.HolonetFeatureSubmittedModalAction;
import de.medo.holonetbridge.commons.discord.ticketing.HolonetSupportChannel;
import de.medo.holonetbridge.commons.discord.ticketing.HolonetSupportChannelRepository;
import de.medo.holonetbridge.commons.discord.ticketing.HolonetTicketCreationSessionStore;
import de.medo.holonetbridge.commons.extapi.TicketDto;
import de.medo.holonetbridge.commons.extapi.TicketProvider;
import de.medo.holonetbridge.commons.provider.HolonetProviderConnection;
import de.medo.holonetbridge.commons.provider.HolonetProviderConnectionRepository;
import de.medo.holonetbridge.commons.provider.HolonetProviderType;
import de.medo.holonetbridge.commons.provider.ProviderConnectionResolver;
import de.medo.holonetbridge.commons.provider.ResolvedProvider;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Proves that a button/ticket action resolves through the database, not through the in-memory
 * {@link HolonetTicketCreationSessionStore}, by driving two completely independent Spring contexts - each
 * with its own fresh, empty in-memory stores - against the very same file-backed H2 database.
 *
 * <p>There is no separate database server process in this project (H2 is embedded), so a literal
 * container-recreation test is not applicable here; fully closing one Spring context and starting a brand
 * new one against the persisted file is the closest faithful equivalent, and exercises exactly the
 * invariant that matters: every in-process, in-memory singleton (including {@code
 * HolonetTicketCreationSessionStore}) is necessarily empty again in the second context, so anything that
 * still works must be resolving through the database.
 *
 * <p>{@code @Nested} classes run in declaration order only because of {@code @TestClassOrder} below; each
 * phase is its own {@code @SpringBootTest} context pointed at the same {@code @TempDir} database file via
 * {@code @DynamicPropertySource}, exactly like {@code MigrationSmokeTest}'s pattern.
 */
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
class TicketSubmissionSurvivesRestartTest {

    private static final long GUILD_ID = 42L;
    private static final long CHANNEL_ID = 4242L;
    private static final long USER_ID = 7L;
    private static final String PROVIDER_BASE_URL = "https://tickets.example.invalid";

    @TempDir
    static Path tempDir;

    @Nested
    @Order(1)
    @SpringBootTest(classes = HolonetBridgeApplication.class, properties = "holonet.discord.token=test-discord-token")
    class BeforeRestart {

        @DynamicPropertySource
        static void databaseUrl(DynamicPropertyRegistry registry) {
            registry.add("spring.datasource.url", () -> "jdbc:h2:file:" + tempDir.resolve("restart-holonet-bridge"));
        }

        @MockitoBean
        private JDA jda;

        @Autowired
        private HolonetSupportChannelRepository supportChannelRepository;

        @Autowired
        private HolonetTicketCreationSessionStore sessionStore;

        @Test
        void tracksASupportChannelAndStartsAnInMemorySessionThatWillNotSurviveTheRestart() {
            supportChannelRepository.save(new HolonetSupportChannel(CHANNEL_ID, GUILD_ID, USER_ID, 1L));
            sessionStore.startOrReset(GUILD_ID, CHANNEL_ID, USER_ID);

            assertThat(supportChannelRepository.findById(CHANNEL_ID)).isPresent();
            assertThat(sessionStore.get(GUILD_ID, CHANNEL_ID, USER_ID)).isPresent();
        }
    }

    @Nested
    @Order(2)
    @SpringBootTest(classes = HolonetBridgeApplication.class, properties = "holonet.discord.token=test-discord-token")
    class AfterRestart {

        @DynamicPropertySource
        static void databaseUrl(DynamicPropertyRegistry registry) {
            registry.add("spring.datasource.url", () -> "jdbc:h2:file:" + tempDir.resolve("restart-holonet-bridge"));
        }

        @MockitoBean
        private JDA jda;

        @MockitoBean
        private ProviderConnectionResolver connectionResolver;

        @Autowired
        private HolonetSupportChannelRepository supportChannelRepository;

        @Autowired
        private HolonetProviderConnectionRepository providerConnectionRepository;

        @Autowired
        private HolonetTicketCreationSessionStore sessionStore;

        @Autowired
        private HolonetFeatureSubmittedModalAction featureSubmit;

        @Test
        @SuppressWarnings("unchecked")
        void ticketSubmissionStillResolvesTheSupportChannelFromTheDatabaseWithNoInMemorySessionLeft()
                throws InterruptedException {
            // The channel row from BeforeRestart is still there - persisted, not in-memory.
            assertThat(supportChannelRepository.findById(CHANNEL_ID)).isPresent();
            // This is a brand-new context: its session store is a brand-new, empty instance.
            assertThat(sessionStore.get(GUILD_ID, CHANNEL_ID, USER_ID)).isEmpty();

            TicketProvider ticketProvider = mock(TicketProvider.class);
            // Persisted, not just mocked: ticketCreated's connectionId must satisfy the real
            // holonet_support_channel -> holonet_provider_connection foreign key when finalizeTicket saves it.
            HolonetProviderConnection connection = providerConnectionRepository.save(new HolonetProviderConnection(
                    UUID.randomUUID(), GUILD_ID, HolonetProviderType.GITEA, PROVIDER_BASE_URL, "acme", "support",
                    true, new EncryptedSecret(new byte[] {1}, new byte[] {2}, "v1"), Instant.now(), Instant.now()));
            when(connectionResolver.forGuild(GUILD_ID))
                    .thenReturn(Optional.of(new ResolvedProvider(connection, ticketProvider)));
            when(ticketProvider.create("[Feature] Dark mode", "description"))
                    .thenReturn(new TicketDto("7", "Dark mode", "description", PROVIDER_BASE_URL + "/acme/support/7"));

            ModalInteractionEvent event = mock(ModalInteractionEvent.class);

            when(event.getUserLocale()).thenReturn(DiscordLocale.GERMAN);

            when(event.getGuildLocale()).thenReturn(DiscordLocale.GERMAN);
            User user = mock(User.class);
            when(user.getIdLong()).thenReturn(USER_ID);
            when(event.getUser()).thenReturn(user);
            Guild guild = mock(Guild.class);
            when(guild.getLocale()).thenReturn(DiscordLocale.GERMAN);
            when(guild.getIdLong()).thenReturn(GUILD_ID);
            when(event.getGuild()).thenReturn(guild);
            when(event.getModalId()).thenReturn(HolonetFeatureModalButton.MODAL_ID);
            ModalMapping titleMapping = textMapping("Dark mode");
            when(event.getValue("title")).thenReturn(titleMapping);
            ModalMapping descriptionMapping = textMapping("description");
            when(event.getValue("description")).thenReturn(descriptionMapping);
            MessageChannelUnion channelUnion = mock(MessageChannelUnion.class);
            when(channelUnion.getIdLong()).thenReturn(CHANNEL_ID);
            TextChannel textChannel = mock(TextChannel.class);
            when(channelUnion.asTextChannel()).thenReturn(textChannel);
            when(event.getChannel()).thenReturn(channelUnion);
            when(textChannel.sendMessage(anyString()))
                    .thenReturn(mock(MessageCreateAction.class, Answers.RETURNS_SELF));
            ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class, Answers.RETURNS_SELF);
            when(event.deferReply(true)).thenReturn(replyAction);
            InteractionHook hook = mock(InteractionHook.class);
            when(hook.getInteraction()).thenReturn(event);
            doAnswer(invocation -> {
                Consumer<InteractionHook> onHook = invocation.getArgument(0);
                onHook.accept(hook);
                return null;
            }).when(replyAction).queue(any());
            CountDownLatch hookReplied = new CountDownLatch(1);
            WebhookMessageCreateAction hookAction = mock(WebhookMessageCreateAction.class, Answers.RETURNS_SELF);
            when(hook.sendMessage(anyString())).thenReturn(hookAction);
            doAnswer(invocation -> {
                hookReplied.countDown();
                return null;
            }).when(hookAction).queue(any(), any());

            featureSubmit.handle(event);

            assertThat(hookReplied.await(5, TimeUnit.SECONDS))
                    .as("deferred reply must be completed via the InteractionHook")
                    .isTrue();
            assertThat(supportChannelRepository.findById(CHANNEL_ID)).hasValueSatisfying(
                    channel -> assertThat(channel.ticketId()).isEqualTo("7"));
        }

        private static ModalMapping textMapping(String value) {
            ModalMapping mapping = mock(ModalMapping.class);
            when(mapping.getAsString()).thenReturn(value);
            return mapping;
        }
    }
}
