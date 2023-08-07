package com.kushui.springbootinit.selector;


import java.util.concurrent.atomic.AtomicInteger;

public class RoundRobinPartitionSelector   {
    private  final int partitionCount;
    private AtomicInteger currentPartition = new AtomicInteger(0);

    public RoundRobinPartitionSelector(int partitionCount) {
        this.partitionCount = partitionCount;
    }


    public int getPartition() {
        return currentPartition.getAndIncrement() % partitionCount;
    }

}