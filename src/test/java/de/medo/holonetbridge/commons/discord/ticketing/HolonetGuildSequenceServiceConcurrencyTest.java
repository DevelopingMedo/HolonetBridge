package de.medo.holonetbridge.commons.discord.ticketing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import de.medo.holonetbridge.HolonetBridgeApplication;
import net.dv8tion.jda.api.JDA;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Proves {@link HolonetGuildSequenceService#nextNumberFor} is safe under real concurrency: a plain
 * {@code @DataJpaTest} cannot exercise this, since {@code @Transactional}/AOP proxying (the mechanism this
 * service's race-freedom actually relies on, see its Javadoc) requires a real Spring-managed bean graph, not
 * a manually constructed one.
 */
@SpringBootTest(classes = HolonetBridgeApplication.class, properties = "holonet.discord.token=test-discord-token")
class HolonetGuildSequenceServiceConcurrencyTest {

    private static final int CONCURRENT_CALLS = 20;

    @MockitoBean
    private JDA jda;

    @Autowired
    private HolonetGuildSequenceService guildSequenceService;

    @Test
    void twentyConcurrentCallsForTheSameGuildEachGetADistinctGaplessNumber() throws InterruptedException {
        long guildId = 123456789L;
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_CALLS);
        try {
            List<Callable<Long>> calls = IntStream.range(0, CONCURRENT_CALLS)
                    .<Callable<Long>>mapToObj(ignored -> () -> guildSequenceService.nextNumberFor(guildId))
                    .toList();

            List<Future<Long>> futures = executor.invokeAll(calls);

            List<Long> numbers = futures.stream().map(this::getResult).collect(Collectors.toList());
            assertThat(numbers).hasSize(CONCURRENT_CALLS).doesNotHaveDuplicates();
            assertThat(numbers.stream().sorted().toList())
                    .isEqualTo(LongStream.range(1, CONCURRENT_CALLS + 1).boxed().toList());
        } finally {
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private Long getResult(Future<Long> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            throw new AssertionError("A concurrent nextNumberFor(...) call failed", exception);
        }
    }
}
