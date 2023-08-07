package com.yupi.springbootinit.scheduled.job;

import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

@Slf4j
public class RetryJob implements Job {
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        Object name = context.getJobDetail().getJobDataMap().get("name");//先得到任务，之后就得到map中的名字
        log.warn(name + "搞卫生");
    }
}
