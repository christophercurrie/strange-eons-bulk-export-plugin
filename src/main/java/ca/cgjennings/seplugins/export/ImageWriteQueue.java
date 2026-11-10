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
 * The memory held by pending images is bounded.
 */
final class ImageWriteQueue implements AutoCloseable {

    /**
     * A write task; returns the file written, or null if nothing was written.
     */
    interface Job {

        File write() throws Exception;
    }

    private static final AtomicInteger threadCount = new AtomicInteger();

    private static final int KIB = 1024;

    private final ExecutorService executor;
    // permits are KiB of image data
    private final Semaphore pending;
    private final int maxPendingKib;
    private final List<File> written = Collections.synchronizedList(new ArrayList<>());
    private final List<String> errors = Collections.synchronizedList(new ArrayList<>());

    /**
     * Creates a queue with one writer per two processors, from 1 to 4, that
     * holds at most a quarter of the maximum heap in pending images.
     */
    static ImageWriteQueue create() {
        Runtime rt = Runtime.getRuntime();
        return new ImageWriteQueue(threadsFor(rt.availableProcessors()), rt.maxMemory() / 4);
    }

    /**
     * Returns the number of writer threads to use for a processor count.
     */
    static int threadsFor(int processors) {
        return Math.max(1, Math.min(4, processors / 2));
    }

    /**
     * Creates a queue.
     *
     * @param threads the number of writer threads
     * @param maxPendingBytes the maximum image data held by queued and running
     * jobs; a job larger than this runs alone
     */
    ImageWriteQueue(int threads, long maxPendingBytes) {
        if (threads < 1 || maxPendingBytes < 1) {
            throw new IllegalArgumentException("threads=" + threads + ", maxPendingBytes=" + maxPendingBytes);
        }
        maxPendingKib = kib(maxPendingBytes, Integer.MAX_VALUE);
        pending = new Semaphore(maxPendingKib);
        executor = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "Bulk export writer " + threadCount.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    private static int kib(long bytes, int max) {
        return (int) Math.max(1L, Math.min(max, (bytes + KIB - 1) / KIB));
    }

    /**
     * Queues a job, blocking until the pending image data leaves room for it.
     *
     * @param bytes the size of the job's image data
     * @throws InterruptedException if interrupted while waiting
     * @throws java.util.concurrent.RejectedExecutionException if closed
     */
    void submit(long bytes, Job job) throws InterruptedException {
        final int cost = kib(bytes, maxPendingKib);
        pending.acquire(cost);
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
                    pending.release(cost);
                }
            });
        } catch (RuntimeException ex) {
            pending.release(cost);
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
