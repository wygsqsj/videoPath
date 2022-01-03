package com.wish.videopath.util;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 线程池用于编解码等工作
 */
public class LiveTaskManager {
    private static volatile LiveTaskManager instance;
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE = Math.max(2, Math.min(CPU_COUNT - 1, 4));
    private static final int MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1;
    private static final int KEEP_ALIVE_SECONDS = 30;
    private static final BlockingQueue<Runnable> sPoolWorkQueue =
            new LinkedBlockingQueue<>(6);
    private static final ThreadPoolExecutor THREAD_POOL_EXECUTOR;

    static {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                sPoolWorkQueue
        );
        executor.allowCoreThreadTimeOut(true);
        THREAD_POOL_EXECUTOR = executor;
    }

    private LiveTaskManager() {
    }

    public static LiveTaskManager getInstance() {
        if (instance == null) {
            synchronized (LiveTaskManager.class) {
                if (instance == null) {
                    instance = new LiveTaskManager();
                }
            }
        }
        return instance;
    }

    public void execute(Runnable runnable) {
        THREAD_POOL_EXECUTOR.execute(runnable);
    }

}
