package ca.cgjennings.seplugins.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ImageWriteQueueTest {

    private static final long MIB = 1024L * 1024L;

    @Test
    void collectsWrittenFiles() throws InterruptedException {
        ImageWriteQueue q = new ImageWriteQueue(2, 3 * MIB);
        for (int i = 0; i < 5; ++i) {
            final File f = new File("out-" + i + ".png");
            q.submit(MIB, () -> f);
        }
        q.submit(MIB, () -> null);
        q.close();
        assertThat(q.getWritten()).hasSize(5).contains(new File("out-0.png"), new File("out-4.png"));
        assertThat(q.getErrors()).isEmpty();
    }

    @Test
    void collectsErrors() throws InterruptedException {
        ImageWriteQueue q = new ImageWriteQueue(1, MIB);
        q.submit(MIB, () -> {
            throw new IOException("disk full");
        });
        q.submit(MIB, () -> {
            throw new IllegalStateException();
        });
        q.close();
        assertThat(q.getWritten()).isEmpty();
        assertThat(q.getErrors()).containsExactly("disk full", "java.lang.IllegalStateException");
    }

    /**
     * Submits a job on another thread and reports whether it was queued
     * within the wait time.
     */
    private static Thread submitLater(ImageWriteQueue q, long bytes, AtomicBoolean queued) {
        Thread t = new Thread(() -> {
            try {
                q.submit(bytes, () -> null);
                queued.set(true);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });
        t.start();
        return t;
    }

    @Test
    void blocksWhenPendingBytesReached() throws Exception {
        ImageWriteQueue q = new ImageWriteQueue(4, 2 * MIB);
        CountDownLatch release = new CountDownLatch(1);
        q.submit(MIB, () -> {
            release.await();
            return null;
        });
        q.submit(MIB, () -> {
            release.await();
            return null;
        });

        AtomicBoolean queued = new AtomicBoolean();
        Thread submitter = submitLater(q, MIB, queued);
        submitter.join(200);
        assertThat(queued).isFalse();

        release.countDown();
        submitter.join(TimeUnit.SECONDS.toMillis(5));
        assertThat(queued).isTrue();
        q.close();
    }

    @Test
    void smallJobsShareBudget() throws Exception {
        ImageWriteQueue q = new ImageWriteQueue(4, 4 * MIB);
        CountDownLatch release = new CountDownLatch(1);
        q.submit(MIB, () -> {
            release.await();
            return null;
        });

        AtomicBoolean queued = new AtomicBoolean();
        Thread submitter = submitLater(q, 2 * MIB, queued);
        submitter.join(TimeUnit.SECONDS.toMillis(5));
        assertThat(queued).isTrue();
        release.countDown();
        q.close();
    }

    @Test
    void oversizedJobRunsAlone() throws Exception {
        ImageWriteQueue q = new ImageWriteQueue(2, MIB);
        CountDownLatch release = new CountDownLatch(1);
        q.submit(10 * MIB, () -> {
            release.await();
            return new File("big.png");
        });

        AtomicBoolean queued = new AtomicBoolean();
        Thread submitter = submitLater(q, 1, queued);
        submitter.join(200);
        assertThat(queued).isFalse();

        release.countDown();
        submitter.join(TimeUnit.SECONDS.toMillis(5));
        assertThat(queued).isTrue();
        q.close();
        assertThat(q.getWritten()).containsExactly(new File("big.png"));
    }

    @Test
    void closeWaitsForJobs() throws InterruptedException {
        ImageWriteQueue q = new ImageWriteQueue(1, MIB);
        q.submit(MIB, () -> {
            Thread.sleep(100);
            return new File("slow.png");
        });
        q.close();
        assertThat(q.getWritten()).containsExactly(new File("slow.png"));
    }

    @Test
    void rejectsJobsAfterClose() throws InterruptedException {
        ImageWriteQueue q = new ImageWriteQueue(1, MIB);
        q.close();
        assertThatThrownBy(() -> q.submit(MIB, () -> null)).isInstanceOf(RejectedExecutionException.class);
        // the rejected job's share of the budget was returned
        AtomicBoolean ran = new AtomicBoolean();
        assertThatThrownBy(() -> q.submit(MIB, () -> {
            ran.set(true);
            return null;
        })).isInstanceOf(RejectedExecutionException.class);
        assertThat(ran).isFalse();
    }

    @Test
    void rejectsInvalidLimits() {
        assertThatThrownBy(() -> new ImageWriteQueue(0, MIB)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ImageWriteQueue(1, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void usesHalfTheProcessorsUpToFour() {
        assertThat(ImageWriteQueue.threadsFor(1)).isEqualTo(1);
        assertThat(ImageWriteQueue.threadsFor(2)).isEqualTo(1);
        assertThat(ImageWriteQueue.threadsFor(6)).isEqualTo(3);
        assertThat(ImageWriteQueue.threadsFor(12)).isEqualTo(4);
    }

    @Test
    void createsDefaultQueue() throws InterruptedException {
        ImageWriteQueue q = ImageWriteQueue.create();
        q.submit(MIB, () -> new File("a.png"));
        q.close();
        assertThat(q.getWritten()).containsExactly(new File("a.png"));
    }
}
