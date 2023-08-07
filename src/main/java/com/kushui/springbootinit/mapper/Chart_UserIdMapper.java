package com.kushui.springbootinit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kushui.springbootinit.model.entity.Chart_UserId;
import org.apache.ibatis.annotations.Param;

public interface Chart_UserIdMapper extends BaseMapper {



    /**
     * 判断表是否存在
     * @param userId
     * @return
     */
    Integer isExitsChart_UserId(@Param("userId") Long userId);

    /**
     *创建表
     * @param createSql
     * @return
     */
    Integer createChart_UserId(@Param("createSql") String createSql);


    /**
     *存储chartData
     * @param userId
     * @return
     */
     Integer insertChartData(@Param("userId") Long userId,@Param(value="chartUserId") Chart_UserId chartUserId);

}
