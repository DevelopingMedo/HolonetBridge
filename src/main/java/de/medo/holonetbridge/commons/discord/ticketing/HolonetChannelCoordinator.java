package de.medo.holonetbridge.commons.discord.ticketing;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;

/** Serializes channel mutations through completion of their remote requests, not just enqueueing. */
@Service
public class HolonetChannelCoordinator {
    private final Object[] locks = IntStream.range(0, 256).mapToObj(_ -> new Object()).toArray();

    public <T> T withChannel(long channelId, Supplier<T> action) {
        synchronized (locks[Math.floorMod(Long.hashCode(channelId), locks.length)]) {
            return action.get();
        }
    }

    public <T> CompletableFuture<T> async(long channelId, Supplier<T> action) {
        return CompletableFuture.supplyAsync(() -> withChannel(channelId, action), Thread::startVirtualThread);
    }

}
