package com.kushui.springbootinit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kushui.springbootinit.model.entity.Chart;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
* @author Admin
* @description 针对表【chart(图表信息表)】的数据库操作Mapper
* @createDate 2023-07-08 18:06:40
* @Entity com.yupi.springbootinit.model.entity.Chart.Chart
*/
public interface ChartMapper extends BaseMapper<Chart> {
        //根据时间和失败状态获取chart
        List<Chart>  getListWithFailedAndTimes(@Param("date") Date date);

        //用于redis分页缓存查询
        List<Long> getIdWithPageAndUser(@Param("current")Long current,@Param("size")Long size,@Param("userId")Long userId);
}




