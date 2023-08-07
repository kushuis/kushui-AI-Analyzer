package com.kushui.springbootinit.mapper;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import com.kushui.springbootinit.model.entity.Chart_UserId;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
public class chart_UserIdTest {

    @Resource
    private Chart_UserIdMapper chartUserIdMapper;

    @Test
    public void  test1(){
        Chart_UserId chartUserId = new Chart_UserId();
        Snowflake snowflake = IdUtil.createSnowflake(1,1);
        long id = snowflake.nextId();
        chartUserId.setId(id);
        chartUserId.setChartData("123456789");
        chartUserIdMapper.insertChartData(1683072316776411137L,chartUserId);
    }
}

