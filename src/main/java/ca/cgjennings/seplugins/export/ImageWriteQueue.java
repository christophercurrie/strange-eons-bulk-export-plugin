package ca.cgjennings.seplugins.export;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Writes images on background threads so that encoding overlaps rendering.
 * The number of pending images is bounded to limit memory use.
 */
final class ImageWriteQueue implements AutoCloseable {

    /**
     * A write task; returns the file written, or null if nothing was written.
     */
    interface Job {

        File write() throws Exception;
    }

    private static final AtomicInteger threadCount = new AtomicInteger();

    private final ExecutorService executor;
    private final Semaphore pending;
    private final List<File> written = Collections.synchronizedList(new ArrayList<>());
    private final List<String> errors = Collections.synchronizedList(new ArrayList<>());

    /**
     * Creates a queue using the default number of threads.
     */
    static ImageWriteQueue create() {
        return new ImageWriteQueue(2, 3);
    }

    /**
     * Creates a queue.
     *
     * @param threads the number of writer threads
     * @param maxPending the maximum number of jobs queued or running
     */
    ImageWriteQueue(int threads, int maxPending) {
        if (threads < 1 || maxPending < threads) {
            throw new IllegalArgumentException("threads=" + threads + ", maxPending=" + maxPending);
        }
        pending = new Semaphore(maxPending);
        executor = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "Bulk export writer " + threadCount.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Queues a job, blocking while the maximum number of jobs are pending.
     *
     * @throws InterruptedException if interrupted while waiting
     * @throws java.util.concurrent.RejectedExecutionException if closed
     */
    void submit(Job job) throws InterruptedException {
        pending.acquire();
        try {
            executor.execute(() -> {
                try {
                    File f = job.write();
                    if (f != null) {
                        written.add(f);
                    }
                } catch (Exception ex) {
                    errors.add(ex.getLocalizedMessage() == null ? ex.toString() : ex.getLocalizedMessage());
                } finally {
                    pending.release();
                }
            });
        } catch (RuntimeException ex) {
            pending.release();
            throw ex;
        }
    }

    /**
     * Waits for all queued jobs to finish and stops the writer threads.
     */
    @Override
    public void close() {
        executor.shutdown();
        boolean interrupted = false;
        while (!executor.isTerminated()) {
            try {
                executor.awaitTermination(1, TimeUnit.MINUTES);
            } catch (InterruptedException ex) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Returns the files written so far.
     */
    List<File> getWritten() {
        synchronized (written) {
            return new ArrayList<>(written);
        }
    }

    /**
     * Returns the errors reported so far.
     */
    List<String> getErrors() {
        synchronized (errors) {
            return new ArrayList<>(errors);
        }
    }
}
