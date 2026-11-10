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

    @Test
    void collectsWrittenFiles() throws InterruptedException {
        ImageWriteQueue q = new ImageWriteQueue(2, 3);
        for (int i = 0; i < 5; ++i) {
            final File f = new File("out-" + i + ".png");
            q.submit(() -> f);
        }
        q.submit(() -> null);
        q.close();
        assertThat(q.getWritten()).hasSize(5).contains(new File("out-0.png"), new File("out-4.png"));
        assertThat(q.getErrors()).isEmpty();
    }

    @Test
    void collectsErrors() throws InterruptedException {
        ImageWriteQueue q = new ImageWriteQueue(1, 1);
        q.submit(() -> {
            throw new IOException("disk full");
        });
        q.submit(() -> {
            throw new IllegalStateException();
        });
        q.close();
        assertThat(q.getWritten()).isEmpty();
        assertThat(q.getErrors()).containsExactly("disk full", "java.lang.IllegalStateException");
    }

    @Test
    void blocksWhenPendingLimitReached() throws Exception {
        ImageWriteQueue q = new ImageWriteQueue(1, 2);
        CountDownLatch release = new CountDownLatch(1);
        q.submit(() -> {
            release.await();
            return null;
        });
        q.submit(() -> null);

        AtomicBoolean thirdQueued = new AtomicBoolean();
        Thread submitter = new Thread(() -> {
            try {
                q.submit(() -> null);
                thirdQueued.set(true);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });
        submitter.start();
        submitter.join(200);
        assertThat(thirdQueued).isFalse();

        release.countDown();
        submitter.join(TimeUnit.SECONDS.toMillis(5));
        assertThat(thirdQueued).isTrue();
        q.close();
    }

    @Test
    void closeWaitsForJobs() throws InterruptedException {
        ImageWriteQueue q = new ImageWriteQueue(1, 1);
        q.submit(() -> {
            Thread.sleep(100);
            return new File("slow.png");
        });
        q.close();
        assertThat(q.getWritten()).containsExactly(new File("slow.png"));
    }

    @Test
    void rejectsJobsAfterClose() throws InterruptedException {
        ImageWriteQueue q = new ImageWriteQueue(1, 1);
        q.close();
        assertThatThrownBy(() -> q.submit(() -> null)).isInstanceOf(RejectedExecutionException.class);
    }

    @Test
    void rejectsInvalidLimits() {
        assertThatThrownBy(() -> new ImageWriteQueue(0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ImageWriteQueue(2, 1)).isInstanceOf(IllegalArgumentException.class);
    }
}
