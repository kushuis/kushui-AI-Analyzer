package com.yupi.springbootinit.job.cycle;

import com.yupi.springbootinit.mapper.ChartMapper;
import com.yupi.springbootinit.model.entity.Chart;
import com.yupi.springbootinit.service.ChartService;
import com.yupi.springbootinit.utils.ExcelUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

@Component
@Slf4j
public class RetryScheduler {

    @Resource
    private ChartMapper chartMapper;

    @Resource
    private ChartService chartService;
    /**
     * 每2分钟执行一次
     * 这个不能设置为每3分钟一次，因为重试的时候结果也可能失败
     */
    @Scheduled(fixedRate =2 * 60 * 1000)
    public void run(){
        //查询近5分钟的数据
        Date fiveMinutesAgoDate = new Date(new Date().getTime() - 5 * 60 * 1000L);
        //获取到5分钟内失败的chart
        List<Chart> charts = chartMapper.getListWithFailedAndTimes(fiveMinutesAgoDate);
        if(charts.isEmpty()){
            log.info("=============定时器查询到状态为failed的chart为空===============");
        }else {
            for (Chart chart :charts) {
                log.info("=============定时器将状态为failed的chart执行异步方法重试===============");
                //下面是拼接userInput
                String chartType = chart.getChartType();
                String csvData = chart.getChartData();

                // 构造用户输入
                StringBuilder userInput = new StringBuilder();
                userInput.append("分析需求：").append("\n");

                // 拼接分析目标
                String userGoal = chart.getGoal();
                if (StringUtils.isNotBlank(chartType)) {
                    userGoal += "，请使用" + chartType;
                }
                userInput.append(userGoal).append("\n");
                userInput.append("原始数据：").append("\n");
                // 压缩后的数据

                userInput.append(csvData).append("\n");
                chartService.genChartByAiAsync(chart,userInput);
                log.info("=============定时器重试成功===============");
            }


//
        }

    }
}
