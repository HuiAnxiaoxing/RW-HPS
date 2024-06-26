package com.github.dr.rwserver.util.threads;

import com.github.dr.rwserver.util.log.Log;

import java.util.concurrent.*;


public class ScheduledThreadPoolExecutorDynamic extends ScheduledThreadPoolExecutor {



    public ScheduledThreadPoolExecutorDynamic(int corePoolSize) {
        super(corePoolSize);
    }

    public ScheduledThreadPoolExecutorDynamic(int corePoolSize, ThreadFactory threadFactory) {
        super(corePoolSize,threadFactory);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        dynamicCorePoolSize(this);
        return super.scheduleAtFixedRate(command, initialDelay, period, unit);
    }

    private static void dynamicCorePoolSize(ScheduledThreadPoolExecutor scheduledThreadPoolExecutor) {
        int activeCount = scheduledThreadPoolExecutor.getActiveCount();
        int corePoolSize = scheduledThreadPoolExecutor.getCorePoolSize();
        final BlockingQueue<Runnable> queue = scheduledThreadPoolExecutor.getQueue();
        Log.clog("当前总线程:"+corePoolSize+"运行中:"+activeCount+"任务数："+queue.size());
        if (activeCount == corePoolSize) {
            scheduledThreadPoolExecutor.setCorePoolSize(corePoolSize + 1);
            Log.clog("》增加一个线程");
        } else if (corePoolSize>3&&activeCount < corePoolSize - 1) {
            scheduledThreadPoolExecutor.setCorePoolSize(corePoolSize - 1);
            Log.clog("》减少一个线程");
        }
    }

    public boolean cancelSchedule(ScheduledFuture<?> scheduledFuture) {
        if (scheduledFuture != null && !scheduledFuture.isCancelled()) {
            scheduledFuture.cancel(true);
            dynamicCorePoolSize(this);
            return true;
        }
        return false;
    }
}