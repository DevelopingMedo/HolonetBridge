# Repository map

Generated with Aider; source HEAD before generation: `68d03ecc98b2cbcca3de1cc2aa5567b753f3f2ab`.

Selected code excerpts, not a complete API or a description of runtime behavior.
Regenerate with `bash scripts/generate-repomap.sh`. See [usage](repomap-guide.md).

```text
src/main/java/de/medo/holonetbridge/commons/ProviderConnectionProperties.java:
⋮
│@ConfigurationProperties(prefix = "holonet.provider")
│@Validated
⋮
│        @NotBlank String token) {
│
⋮
│    @Override
│    public String toString() {
⋮

src/main/java/de/medo/holonetbridge/commons/crypto/EncryptedSecret.java:
⋮
│public record EncryptedSecret(byte[] ciphertext, byte[] nonce, String keyVersion) {
│
│    public EncryptedSecret {
│        Objects.requireNonNull(ciphertext, "ciphertext must not be null");
│        Objects.requireNonNull(nonce, "nonce must not be null");
│        Objects.requireNonNull(keyVersion, "keyVersion must not be null");
│        ciphertext = ciphertext.clone();
│        nonce = nonce.clone();
│    }
│
│    @Override
│    public byte[] ciphertext() {
⋮
│    @Override
│    public byte[] nonce() {
⋮
│    @Override
│    public boolean equals(Object o) {
⋮
│    @Override
│    public int hashCode() {
⋮
│    @Override
│    public String toString() {
⋮

src/main/java/de/medo/holonetbridge/commons/crypto/EnvironmentMasterKeyProvider.java:
⋮
│@Component
│public class EnvironmentMasterKeyProvider implements MasterKeyProvider {
│
⋮
│    @Override
│    public SecretKey keyForVersion(String keyVersion) {
⋮

src/main/java/de/medo/holonetbridge/commons/crypto/MasterKeyProperties.java:
⋮
│@ConfigurationProperties(prefix = "holonet.crypto")
│public record MasterKeyProperties(String activeKeyVersion, Map<String, String> keys) {
│
⋮
│    @Override
│    public String toString() {
⋮

src/main/java/de/medo/holonetbridge/commons/crypto/MasterKeyProvider.java:
⋮
│public interface MasterKeyProvider {
│
│    /** The key new encryptions are performed with, together with its version. */
│    VersionedKey activeKey();
│
│    /** The key a ciphertext recorded as {@code keyVersion} must be decrypted with. */
│    SecretKey keyForVersion(String keyVersion);
│
│    /** A master key together with the version identifier it is stored/looked up under. */
│    record VersionedKey(String version, SecretKey key) {}
⋮

src/main/java/de/medo/holonetbridge/commons/crypto/MasterKeyUnavailableException.java:
⋮
│public class MasterKeyUnavailableException extends RuntimeException {
│
│    public MasterKeyUnavailableException(String message) {
│        super(message);
│    }
⋮

src/main/java/de/medo/holonetbridge/commons/crypto/TokenCryptoService.java:
⋮
│public interface TokenCryptoService {
│
│    /** Encrypts {@code plaintextToken}, authenticating it against {@code context}. */
│    EncryptedSecret encrypt(String plaintextToken, TokenEncryptionContext context);
│
│    /**
│     * Decrypts {@code secret}, verifying it was encrypted under {@code context}.
│     *
│     * @throws TokenCryptoException if the ciphertext, nonce, or context do not match what the toke
│     *     originally encrypted with (tampering, corruption, or a mismatched guild/connection), or
⋮

src/main/java/de/medo/holonetbridge/commons/discord/DiscordProperties.java:
⋮
│@ConfigurationProperties(prefix = "holonet.discord")
│@Validated
│public record DiscordProperties(@NotBlank String token) {
│
│    @Override
│    public String toString() {
⋮

src/main/java/de/medo/holonetbridge/commons/discord/command/HolonetSetupCommandAction.java:
⋮
│@Component
│public class HolonetSetupCommandAction {
│
⋮
│    private static void replyError(SlashCommandInteractionEvent event, String message) {
│        event.reply(message).setEphemeral(true).queue();
⋮

src/main/java/de/medo/holonetbridge/commons/discord/modal/HolonetProviderConnectionModal.java:
⋮
│public final class HolonetProviderConnectionModal {
│
│    /** Custom id of the Gitea provider-connection modal. */
│    public static final String MODAL_ID = "setup:modal:provider-gitea";
│
│    public static final String INPUT_BASE_URL = "base_url";
│    public static final String INPUT_OWNER = "owner";
│    public static final String INPUT_REPOSITORY = "repository";
│    public static final String INPUT_TOKEN = "token";
│
⋮
│    public static Modal build(boolean tokenRequired, HolonetSetupDraft prefill) {
│        TextInput baseUrl = TextInput.create(INPUT_BASE_URL, TextInputStyle.SHORT)
│                .setRequiredRange(1, 200)
│                .setPlaceholder("https://gitea.example.com")
│                .setValue(prefill == null ? null : prefill.baseUrl())
│                .build();
│        TextInput owner = TextInput.create(INPUT_OWNER, TextInputStyle.SHORT)
│                .setRequiredRange(1, 100)
│                .setValue(prefill == null ? null : prefill.owner())
│                .build();
⋮

src/main/java/de/medo/holonetbridge/commons/discord/modal/HolonetTicketSubmitter.java:
⋮
│@Slf4j
│@Component
│public class HolonetTicketSubmitter {
│
⋮
│    private void markBroken(long channelId) {
│        supportChannelRepository.findById(channelId).ifPresent(channel -> {
│            channel.markBroken();
│            supportChannelRepository.save(channel);
│        });
⋮

src/main/java/de/medo/holonetbridge/commons/discord/select/HolonetGrantWatcherAction.java:
⋮
│@Component
│public class HolonetGrantWatcherAction {
│
⋮
│    private void grantWatcher(EntitySelectInteractionEvent event, Member member) {
│        event.deferReply(true).queue();
│        event.getChannel()
│                .asTextChannel()
│                .upsertPermissionOverride(member)
│                .grant(
│                        Permission.VIEW_CHANNEL,
│                        Permission.MESSAGE_SEND,
│                        Permission.MESSAGE_HISTORY,
│                        Permission.MESSAGE_ATTACH_FILES)
⋮

src/main/java/de/medo/holonetbridge/commons/discord/setup/HolonetDiscordServer.java:
⋮
│@Entity
│@Table(name = "holonet_discord_server")
│public class HolonetDiscordServer {
│
⋮
│    public long guildId() {
│        return guildId;
⋮
│    public long channelId() {
│        return channelId;
⋮
│    public long messageId() {
│        return messageId;
⋮

src/main/java/de/medo/holonetbridge/commons/discord/setup/HolonetSetupDraft.java:
⋮
│public final class HolonetSetupDraft {
│
│    private final long guildId;
│    private final long panelChannelId;
│
│    private String baseUrl;
│    private String owner;
│    private String repository;
│
│    public HolonetSetupDraft(long guildId, long panelChannelId) {
⋮
│    public long panelChannelId() {
│        return panelChannelId;
⋮
│    public void lastSubmittedTarget(String baseUrl, String owner, String repository) {
│        this.baseUrl = baseUrl;
│        this.owner = owner;
│        this.repository = repository;
⋮

src/main/java/de/medo/holonetbridge/commons/discord/ticketing/HolonetGuildSequence.java:
⋮
│@Entity
│@Table(name = "holonet_guild_sequence")
│public class HolonetGuildSequence {
│
⋮
│    public long guildId() {
│        return guildId;
⋮
│    public long currentNumber() {
│        return currentNumber;
⋮

src/main/java/de/medo/holonetbridge/commons/discord/ticketing/HolonetGuildSequenceRepository.java:
⋮
│public interface HolonetGuildSequenceRepository extends JpaRepository<HolonetGuildSequence, Long> {
│
│    /**
│     * Acquires an exclusive, commit-held row lock on the guild's counter (translates to {@code SEL
│     * FOR UPDATE}) - the actual mutual-exclusion guarantee {@link HolonetGuildSequenceTransaction}
│     * before calling {@link #increment}. A blind {@code UPDATE s.currentNumber = s.currentNumber +
│     * is not sufficient for that on every database: without first establishing this lock, two conc
│     * transactions can each still base their {@code + 1} on the same pre-update value and both com
│     * same "next" number instead of one of them blocking until the other has committed.
│     */
│    @Lock(LockModeType.PESSIMISTIC_WRITE)
│    @Query("select s from HolonetGuildSequence s where s.guildId = :guildId")
│    Optional<HolonetGuildSequence> lockForUpdate(@Param("guildId") long guildId);
│
⋮

src/main/java/de/medo/holonetbridge/commons/discord/ticketing/HolonetGuildSequenceTransaction.java:
⋮
│@Component
│class HolonetGuildSequenceTransaction {
│
⋮
│    @Transactional
│    long incrementAndGet(long guildId) {
⋮

src/main/java/de/medo/holonetbridge/commons/discord/ticketing/HolonetSupportChannel.java:
⋮
│@Entity
│@Table(name = "holonet_support_channel")
│public class HolonetSupportChannel {
│
⋮
│    public long channelId() {
│        return channelId;
⋮
│    public long guildId() {
│        return guildId;
⋮
│    public long requesterUserId() {
│        return requesterUserId;
⋮
│    public UUID connectionId() {
│        return connectionId;
⋮
│    public String ticketId() {
│        return ticketId;
⋮
│    public String ticketUrl() {
│        return ticketUrl;
⋮
│    public void ticketCreated(UUID connectionId, String ticketId, String ticketUrl) {
│        this.connectionId = connectionId;
│        this.ticketId = ticketId;
│        this.ticketUrl = ticketUrl;
│        this.status = HolonetSupportChannelStatus.OPEN;
⋮
│    public void markBroken() {
│        this.status = HolonetSupportChannelStatus.BROKEN;
⋮
│    void linkLegacyConnection(UUID connectionId) {
│        this.connectionId = connectionId;
⋮

src/main/java/de/medo/holonetbridge/commons/discord/ticketing/HolonetTicketCreationSession.java:
⋮
│public final class HolonetTicketCreationSession {
│
│    private HolonetTicketKind kind;
│
│    public HolonetTicketKind kind() {
│        return kind;
│    }
│
│    public void kind(HolonetTicketKind kind) {
│        this.kind = kind;
⋮

src/main/java/de/medo/holonetbridge/commons/domain/ticket/HolonetTicket.java:
⋮
│@Getter
│@SuperBuilder
│public class HolonetTicket {
│
⋮

src/main/java/de/medo/holonetbridge/commons/extapi/TicketProvider.java:
⋮
│public interface TicketProvider {
│
│    TicketDto create(String title, String description);
│
│    Optional<TicketDto> read(String id);
│
│    TicketDto edit(String id, String title, String description);
│
│    void comment(String id, String body);
⋮

src/main/java/de/medo/holonetbridge/commons/markdown/MarkdownBuilder.java:
⋮
│public final class MarkdownBuilder {
│
│    private final TextBuilder builder = new TextBuilder();
│    private boolean hasBlock;
│
│    private MarkdownBuilder() {}
│
│    public static MarkdownBuilder builder() {
│        return new MarkdownBuilder();
│    }
│
⋮
│    public String build() {
│        return builder.toString();
⋮

src/main/java/de/medo/holonetbridge/commons/net/SsrfBlockedTargetException.java:
⋮
│public class SsrfBlockedTargetException extends RuntimeException {
│
│    public SsrfBlockedTargetException(String message) {
│        super(message);
│    }
│
│    public SsrfBlockedTargetException(String message, Throwable cause) {
│        super(message, cause);
│    }
⋮

src/main/java/de/medo/holonetbridge/commons/net/SsrfSafeHttpClientFactory.java:
⋮
│@Component
│public class SsrfSafeHttpClientFactory {
│
⋮
│    public RestClient.Builder restClientBuilderFor(URI baseUrl) {
│        return RestClient.builder()
│                .baseUrl(baseUrl.toString())
│                .requestFactory(new HttpComponentsClientHttpRequestFactory(httpClientFor(baseUrl)))
⋮

src/main/java/de/medo/holonetbridge/commons/net/SsrfSafeUrlValidator.java:
⋮
│@Component
│public class SsrfSafeUrlValidator {
│
⋮
│    public boolean isAllowlisted(String host, int port) {
│        String target = host.toLowerCase(Locale.ROOT) + ":" + port;
│        return policy.allowedInternalTargets().stream()
│                .map(entry -> entry.toLowerCase(Locale.ROOT))
│                .anyMatch(target::equals);
⋮

src/main/java/de/medo/holonetbridge/commons/provider/HolonetProviderConnection.java:
⋮
│@Entity
│@Table(name = "holonet_provider_connection")
│public class HolonetProviderConnection {
│
⋮
│    public long guildId() {
│        return guildId;
⋮
│    private void applySecret(EncryptedSecret secret) {
│        this.secretCiphertext = secret.ciphertext();
│        this.secretNonce = secret.nonce();
│        this.secretKeyVersion = secret.keyVersion();
⋮

src/main/java/de/medo/holonetbridge/commons/provider/HolonetProviderConnectionRepository.java:
⋮
│public interface HolonetProviderConnectionRepository extends JpaRepository<HolonetProviderConnectio
│
│    Optional<HolonetProviderConnection> findByGuildIdAndActiveTrue(long guildId);
│
│    List<HolonetProviderConnection> findAllByGuildId(long guildId);
⋮

src/main/java/de/medo/holonetbridge/commons/provider/HolonetProviderConnectionService.java:
⋮
│@Service
│public class HolonetProviderConnectionService {
│
⋮
│    public Optional<HolonetProviderConnection> activeConnection(long guildId) {
│        return repository.findByGuildIdAndActiveTrue(guildId);
⋮

src/main/java/de/medo/holonetbridge/gitea/GiteaProviderFactory.java:
⋮
│@Component
│public class GiteaProviderFactory {
│
⋮
│    public TicketProvider create(URI baseUrl, String token, String owner, String repository) {
│        return new GiteaTicketProvider(httpClientFactory.restClientBuilderFor(baseUrl), baseUrl, to
⋮

src/main/java/de/medo/holonetbridge/gitea/GiteaTicketProvider.java:
⋮
│public class GiteaTicketProvider implements TicketProvider {
│
│    private final RestClient restClient;
│    private final String owner;
│    private final String repository;
│
│    public GiteaTicketProvider(
│            RestClient.Builder restClientBuilder, URI baseUrl, String token, String owner, String r
│        this.restClient = restClientBuilder
│                .baseUrl(baseUrl.toString())
⋮
│    @Override
│    public TicketDto create(String title, String description) {
⋮

src/test/java/de/medo/holonetbridge/bootstrap/BootstrapSmokeTest.java:
⋮
│@SpringBootTest(classes = HolonetBridgeApplication.class, properties = "holonet.discord.token=test-
│class BootstrapSmokeTest {
│
⋮
│    @Test
│    void startsSuccessfullyWithNoProviderConnectionConfiguredAtAll() {
⋮

src/test/java/de/medo/holonetbridge/commons/crypto/TokenCryptoServiceTest.java:
⋮
│class TokenCryptoServiceTest {
│
│    private final FakeMasterKeyProvider masterKeyProvider = new FakeMasterKeyProvider("v1");
│    private final TokenCryptoService cryptoService = new AesGcmTokenCryptoService(masterKeyProvider
│    private final TokenEncryptionContext context = new TokenEncryptionContext(123L, "connection-1")
│
│    @Test
│    void decryptingTheEncryptedTokenWithTheSameContextReturnsTheOriginalPlaintext() {
│        EncryptedSecret secret = cryptoService.encrypt("super-secret-token", context);
│
⋮
│    private static final class FakeMasterKeyProvider implements MasterKeyProvider {
│
│        private final Map<String, SecretKey> keysByVersion = new HashMap<>();
│        private String activeVersion;
│
│        FakeMasterKeyProvider(String initialVersion) {
│            addKey(initialVersion);
│            activeVersion = initialVersion;
│        }
│
⋮
│        @Override
│        public SecretKey keyForVersion(String keyVersion) {
⋮

```
