package org.example.thread;

import lombok.NonNull;

import java.util.concurrent.*;

public class RaftThreadPool {

    private static final int cup = Runtime.getRuntime().availableProcessors();
    private static final int maxPoolSize = cup * 2;
    private static final int queueSize = 1024;
    private static final long keepTime = 1000 * 60;
    private static final TimeUnit keepTimeUnit = TimeUnit.MILLISECONDS;

    private static final ScheduledExecutorService ss = getScheduled();
    private static final ThreadPoolExecutor te = getThreadPool();

    private static ThreadPoolExecutor getThreadPool() {
        return new ThreadPoolExecutor(
                cup,
                maxPoolSize,
                keepTime,
                keepTimeUnit,
                new LinkedBlockingQueue<>(queueSize),
                new NameThreadFactory());
    }

    private static ScheduledExecutorService getScheduled() {
        return new ScheduledThreadPoolExecutor(cup, new NameThreadFactory());
    }


    public static void scheduleAtFixedRate(Runnable r, long initDelay, long delay) {
        ss.scheduleAtFixedRate(r, initDelay, delay, TimeUnit.MILLISECONDS);
    }


    public static void scheduleWithFixedDelay(Runnable r, long delay) {
        ss.scheduleWithFixedDelay(r, 0, delay, TimeUnit.MILLISECONDS);
    }

    @SuppressWarnings("unchecked")
    public static <T> Future<T> submit(Callable r) {
        return te.submit(r);
    }

    public static void execute(Runnable r) {
        te.execute(r);
    }

    public static void execute(Runnable r, boolean sync) {
        if (sync) {
            r.run();
        } else {
            te.execute(r);
        }
    }

    static class NameThreadFactory implements ThreadFactory {

        @Override
        public Thread newThread(@NonNull Runnable r) {
            Thread t = new Thread(r, "Raft thread");
            t.setDaemon(true);
            t.setPriority(5);
            return t;
        }
    }

}
