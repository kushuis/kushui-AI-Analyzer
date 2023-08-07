package com.yupi.springbootinit.config;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.yupi.springbootinit.selector.RoundRobinPartitionSelector;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Slf4j
public class BackPressureExecutor implements Executor {
    private  final List<ExecutorService> executors;

    private final RoundRobinPartitionSelector partitioner;

    private Long rejectSleepMills = 1L;

    public BackPressureExecutor(String name, int executorNumber, int coreSize, int maxSize, int capacity, long rejectSleepMills) {

        this.rejectSleepMills = rejectSleepMills;

        this.executors = new ArrayList<>(executorNumber);

        for (int i = 0; i < executorNumber; i++) {

            ArrayBlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(capacity);

            this.executors.add(new ThreadPoolExecutor(

                    coreSize, maxSize, 0L, TimeUnit.MILLISECONDS,

                    queue,

                    new ThreadFactoryBuilder().setNameFormat(name + "-" + i + "-%d").build(),

                    new ThreadPoolExecutor.AbortPolicy()));

        }

        this.partitioner = new RoundRobinPartitionSelector(executorNumber);

    }

    @Override
    public void execute(@NotNull Runnable command) {
        boolean rejected;

        do {

            try {
                rejected = false;
                //通过轮询算法从list中获取线程池然后获取线程然后执行lambda表达式中的实现
                //倘若获取线程失败捕获线程池拒绝异常，当前线程进入睡眠10ms，然后继续去争取线程
                executors.get(partitioner.getPartition()).execute(command);
            } catch (RejectedExecutionException e) {

                rejected = true;

                try {

                    TimeUnit.MILLISECONDS.sleep(rejectSleepMills);

                } catch (InterruptedException e1) {

                    log.warn("Reject sleep has been interrupted.", e1);

                }

            }

        } while (rejected);
    }
}
