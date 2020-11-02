package net.openhft.chronicle.queue.impl.single;

import net.openhft.chronicle.core.time.SetTimeProvider;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ChronicleQueueTestBase;
import net.openhft.chronicle.queue.impl.RollingChronicleQueue;
import net.openhft.chronicle.wire.DocumentContext;
import org.junit.Test;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class QueueLockTest extends ChronicleQueueTestBase {

    @Test
    public void testTimeout() throws InterruptedException {
        check(true);
    }

    @Test
    public void testRecover() throws InterruptedException {
        check(false);
    }

    private void check(boolean shouldThrowException) throws InterruptedException {
        try {
            System.setProperty("queue.dont.recover.lock.timeout", Boolean.toString(shouldThrowException));

            final long timeoutMs = 500;
            final File queueDir = getTmpDir();
            final SetTimeProvider timeProvider = new SetTimeProvider();
            try (final RollingChronicleQueue queue = ChronicleQueue.singleBuilder(queueDir).
                    timeProvider(timeProvider).timeoutMS(timeoutMs).
                    build()) {

                // lock the queue
                queue.acquireAppender().writingDocument();

                final CountDownLatch started = new CountDownLatch(1);
                final CountDownLatch finished = new CountDownLatch(1);
                final AtomicBoolean recoveredAndAcquiredTheLock = new AtomicBoolean();

                final Thread otherWriter = new Thread(() -> {
                    started.countDown();
                    try (DocumentContext ignored = queue.acquireAppender().writingDocument()) {
                        recoveredAndAcquiredTheLock.set(true);
                    } finally {
                        finished.countDown();
                    }
                });

                otherWriter.start();
                started.await(1, TimeUnit.SECONDS);
                long startTime = System.currentTimeMillis();
                finished.await(1, TimeUnit.SECONDS);
                long endTime = System.currentTimeMillis();
                assertTrue("timeout", endTime >= startTime + timeoutMs);
                assertEquals(shouldThrowException, !recoveredAndAcquiredTheLock.get());
            }
        } finally {
            System.clearProperty("queue.dont.recover.lock.timeout");
        }
    }
}
