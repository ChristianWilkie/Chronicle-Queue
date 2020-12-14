package net.openhft.chronicle.queue;

import net.openhft.chronicle.core.OS;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueue;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import org.junit.Test;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class DumpQueueMainTest extends ChronicleQueueTestBase {

    @Test
    public void shouldBeAbleToDumpReadOnlyQueueFile() throws IOException {
        if (OS.isWindows())
            return;

        final File dataDir = getTmpDir();
        try (final ChronicleQueue queue = SingleChronicleQueueBuilder.
                binary(dataDir).
                build()) {

            final ExcerptAppender excerptAppender = queue.acquireAppender();
            excerptAppender.writeText("first");
            excerptAppender.writeText("last");

            try (Stream<Path> list = Files.list(dataDir.toPath())) {
                final Path queueFile = list.
                        filter(p -> p.toString().endsWith(SingleChronicleQueue.SUFFIX)).
                        findFirst().orElseThrow(() ->
                        new AssertionError("Could not find queue file in directory " + dataDir));
                assertTrue(queueFile.toFile().setWritable(false));

                final CountingOutputStream countingOutputStream = new CountingOutputStream();
                DumpQueueMain.dump(queueFile.toFile(), new PrintStream(countingOutputStream), Long.MAX_VALUE);

                assertThat(countingOutputStream.bytes, is(not(0L)));
            }
        }
    }

    @Test
    public void shouldDumpDirectoryListing() {
        final File dataDir = getTmpDir();
        try (final ChronicleQueue queue = SingleChronicleQueueBuilder.
                binary(dataDir).
                build()) {

            final ExcerptAppender excerptAppender = queue.acquireAppender();
            excerptAppender.writeText("first");
            excerptAppender.writeText("last");

            final ByteArrayOutputStream capture = new ByteArrayOutputStream();
            DumpQueueMain.dump(dataDir, new PrintStream(capture), Long.MAX_VALUE);

            final String capturedOutput = new String(capture.toByteArray());

            assertThat(capturedOutput, containsString("listing.highestCycle"));
            assertThat(capturedOutput, containsString("listing.lowestCycle"));
        }
    }

    private static final class CountingOutputStream extends OutputStream {
        private long bytes;

        @Override
        public void write(final int b) throws IOException {
            bytes++;
        }
    }
}