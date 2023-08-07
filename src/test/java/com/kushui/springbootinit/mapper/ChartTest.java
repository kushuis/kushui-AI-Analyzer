package com.kushui.springbootinit.mapper;


import com.kushui.springbootinit.model.entity.Chart;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

@SpringBootTest
public class ChartTest {

    @Resource
    private ChartMapper chartMapper;

    @Test
    void getListWithFailedAndTimes() {
        Date fiveMinutesAgoDate = new Date(new Date().getTime() - 5 * 60 * 1000L);
        List<Chart> charts = chartMapper.getListWithFailedAndTimes(fiveMinutesAgoDate);
        System.out.println("===================================================");
        System.out.println(charts);
    }
}
