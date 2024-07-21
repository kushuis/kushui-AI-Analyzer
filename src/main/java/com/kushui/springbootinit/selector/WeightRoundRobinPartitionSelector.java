package com.kushui.springbootinit.selector;


import com.kushui.springbootinit.common.Weight;
import com.kushui.springbootinit.constant.WeightRoundRobinConstant;

import java.util.ArrayList;
import java.util.List;

public class WeightRoundRobinPartitionSelector {
    private  final int partitionCount;
//    private AtomicInteger currentPartition = new AtomicInteger(0);

    public static List<Weight> currWeights = new ArrayList<>();

    //获取权重总值
    public static int totalWeights = WeightRoundRobinConstant.LIST.stream().mapToInt(w ->w).sum();


    public WeightRoundRobinPartitionSelector(int partitionCount) {
        this.partitionCount = partitionCount;
    }


    public static int getPartition() {
        //启动三个线程池的情况
//        if (partitionCount != 3){
//            throw new BusinessException(PARAMS_ERROR);
//        }

        //平滑加权轮询算法

//        //获取权重总值
//        int totalWeights = WeightRoundRobinConstant.LIST.stream().mapToInt(w ->w).sum();

        if (currWeights.isEmpty()){
            WeightRoundRobinConstant.LIST.forEach((weight)->{
                currWeights.add(new Weight(weight,0));
            });
        }
        //增加权值，相当于ABC树分别长高2，3，5
        for (Weight weight :currWeights) {
            weight.setCurrentWeight(weight.getCurrentWeight()+weight.getWeight());
        }
        //找最大值
        Weight maxCurrentWeight = null;
        for (Weight weight: currWeights){
            if (maxCurrentWeight == null || weight.getCurrentWeight() > maxCurrentWeight.getCurrentWeight()){
                maxCurrentWeight = weight;
            }
        }
        int index = currWeights.indexOf(maxCurrentWeight);
        currWeights.get(index).setCurrentWeight(maxCurrentWeight.getCurrentWeight() - totalWeights);

//        maxCurrentWeight.setCurrentWeight(maxCurrentWeight.getCurrentWeight() - totalWeights);

        return index;

//        return currentPartition.getAndIncrement() % partitionCount;

    }

}