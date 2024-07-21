package com.kushui.springbootinit.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.rholder.retry.*;
import com.google.common.base.Predicates;
import com.kushui.springbootinit.mapper.ChartMapper;
import com.kushui.springbootinit.listener.MyRetryListener;
import com.kushui.springbootinit.manager.AiManager;
import com.kushui.springbootinit.model.entity.Chart;


import com.kushui.springbootinit.service.ChartService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.concurrent.*;

/**
* @author Admin
* @description 针对表【chart(图表信息表)】的数据库操作Service实现
* @createDate 2023-07-08 18:12:38
*/
@Service
public class ChartServiceImpl extends ServiceImpl<ChartMapper, Chart>
    implements ChartService {

    @Resource
    private AiManager aiManager;


    @Resource
    private ThreadPoolExecutor threadPoolExecutor;

    //调用第三方AI的标识号
     long biModelId = 1659171950288818178L;


    @Override
    public void genChartByAiAsync(Chart chart,StringBuilder userInput) {

        // 定义任务重试器
        Retryer<Boolean> retryer = RetryerBuilder.<Boolean>newBuilder()
                .retryIfResult(Predicates.<Boolean>isNull()) // 如果结果为空则重试
                .retryIfResult(Predicates.equalTo(false))//结果为false时重试
                .retryIfExceptionOfType(IOException.class) // 发生IO异常则重试
                .retryIfRuntimeException() // 发生运行时异常则重试
                //重试后，出现异常待5s后重试，再出现异常，等待10s后重试
                .withWaitStrategy(WaitStrategies.incrementingWait(5, TimeUnit.SECONDS, 5, TimeUnit.SECONDS)) // 等待
                .withStopStrategy(StopStrategies.stopAfterAttempt(3)) // 允许执行4次（首次执行 + 最多重试3次）
                .withRetryListener(new MyRetryListener())
                .build();

        // todo 建议处理任务队列满了后，抛异常的情况
        //该异步线程不返回值，而是不断改变chart状态，并将最终结果保存到数据库
        CompletableFuture.runAsync(() -> {


            // 先修改图表任务状态为 “执行中”。等执行成功后，修改为 “已完成”、保存执行结果；执行失败后，状态修改为 “失败”，记录任务失败信息。
            Chart updateChart = new Chart();
            updateChart.setId(chart.getId());
            updateChart.setStatus("running");
            boolean b = this.updateById(updateChart);
            if (!b) {
                handleChartUpdateError(chart.getId(), "更新图表执行中状态失败");
                return;
            }

            Callable<Boolean> callable = () -> {

                //调用第三方接口AI
                String result = aiManager.doChat(biModelId, userInput.toString());
                String[] splits = result.split("【【【【【");
                if (splits.length < 3) {
                    handleChartUpdateError(chart.getId(), "AI 生成错误");
                    return false;
                }
                String genChart = splits[1].trim();
                String genResult = splits[2].trim();
                if (StringUtils.isBlank(genChart) || StringUtils.isBlank(genResult)){
                    return false;
                }
                Chart updateChartResult = new Chart();
                updateChartResult.setId(chart.getId());
                updateChartResult.setGenChart(genChart);
                updateChartResult.setGenResult(genResult);
                // todo 建议定义状态为枚举值
                updateChartResult.setStatus("succeed");
                boolean updateResult = this.updateById(updateChartResult);
                if (!updateResult) {
                    handleChartUpdateError(chart.getId(), "更新图表succeed状态失败");
                    return false;
                }

                return true;
            };
            try {
                retryer.call(callable); // 执行
            } catch (RetryException | ExecutionException e) { // 重试次数超过阈值或被强制中断
                e.printStackTrace();
            }

        }, threadPoolExecutor);}

    private void handleChartUpdateError(long chartId, String execMessage) {
        Chart updateChartResult = new Chart();
        updateChartResult.setId(chartId);
        updateChartResult.setStatus("failed");
        updateChartResult.setExecMessage(execMessage);
        boolean updateResult = this.updateById(updateChartResult);
        if (!updateResult) {
            log.error("更新图表失败状态失败" + chartId + "," + execMessage);
        }
    }
}




