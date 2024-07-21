package com.kushui.springbootinit.common;

import lombok.Data;

@Data
public class Weight {
//    private String ip;

    private Integer weight;

    private Integer currentWeight;

    public Weight(Integer weight,Integer currentWeight){

//        this.ip = ip;
        this.weight = weight;
        this.currentWeight = currentWeight;

    }

}
