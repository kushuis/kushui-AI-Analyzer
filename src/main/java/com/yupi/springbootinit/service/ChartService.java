package com.yupi.springbootinit.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.springbootinit.model.entity.Chart;

/**
* @author Admin
* @description 针对表【chart(图表信息表)】的数据库操作Service
* @createDate 2023-07-08 18:06:40
*/
public interface ChartService extends IService<Chart> {

        //异步请求获取AI结果
        void genChartByAiAsync(Chart chart,StringBuilder userInput);
}
