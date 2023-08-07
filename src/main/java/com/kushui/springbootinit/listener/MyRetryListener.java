package com.kushui.springbootinit.listener;

import com.github.rholder.retry.Attempt;
import com.github.rholder.retry.RetryListener;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class MyRetryListener implements RetryListener {


//    @Resource
//    private ChartService chartService;
//    private Long chartId;



//    public MyRetryListener(Long chartId){
//        this.chartId = chartId;
//    }
    @Override
    public <T> void onRetry(Attempt<T> attempt) {
        // 第几次重试,(注意:第一次重试其实是第一次调用)
        System.out.println("==============进入监听器==================");
        //handleChartUpdateError(chartId, "第"+attempt.getAttemptNumber()+"次调用第三方接口失败");
        System.out.print("[retry]time=" + attempt.getAttemptNumber());

        // 距离第一次重试的延迟
        System.out.print(",delay=" + attempt.getDelaySinceFirstAttempt());

        // 重试结果: 是异常终止, 还是正常返回
        System.out.print(",hasException=" + attempt.hasException());
        System.out.print(",hasResult=" + attempt.hasResult());

        // 是什么原因导致异常
        if (attempt.hasException()) {
            System.out.print(",causeBy=" + attempt.getExceptionCause().toString());
        } else {
            // 正常返回时的结果
            System.out.print(",result=" + attempt.getResult());
        }
    }

//    private void handleChartUpdateError(long chartId, String execMessage) {
//        Chart updateChartResult = new Chart();
//        updateChartResult.setId(chartId);
//        updateChartResult.setStatus("failed");
//        updateChartResult.setExecMessage(execMessage);
//        boolean updateResult = chartService.updateById(updateChartResult);
//        if (!updateResult) {
//            log.error("监听器中更新图表失败状态失败" + chartId + "," + execMessage);
//        }
//    }
}
