package com.kushui.springbootinit.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.rholder.retry.*;
import com.google.common.base.Predicates;
import com.google.gson.Gson;
import com.kushui.springbootinit.bizmq.BiMessageProducer;
import com.kushui.springbootinit.mapper.ChartMapper;
import com.kushui.springbootinit.mapper.Chart_UserIdMapper;
import com.kushui.springbootinit.annotation.AuthCheck;
import com.kushui.springbootinit.common.BaseResponse;
import com.kushui.springbootinit.common.DeleteRequest;
import com.kushui.springbootinit.common.ErrorCode;
import com.kushui.springbootinit.common.ResultUtils;
import com.kushui.springbootinit.config.BackPressureExecutor;
import com.kushui.springbootinit.constant.ChartConstant;
import com.kushui.springbootinit.constant.CommonConstant;
import com.kushui.springbootinit.constant.UserConstant;
import com.kushui.springbootinit.exception.BusinessException;
import com.kushui.springbootinit.exception.ThrowUtils;
import com.kushui.springbootinit.listener.MyRetryListener;
import com.kushui.springbootinit.manager.AiManager;
import com.kushui.springbootinit.manager.RedisLimiterManager;
import com.kushui.springbootinit.model.dto.chart.*;
import com.kushui.springbootinit.model.dto.chart.*;

import com.kushui.springbootinit.model.entity.Chart;

import com.kushui.springbootinit.model.entity.Chart_UserId;
import com.kushui.springbootinit.model.entity.User;
import com.kushui.springbootinit.model.vo.BiResponse;
import com.kushui.springbootinit.service.ChartService;
import com.kushui.springbootinit.service.UserService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import com.kushui.springbootinit.utils.ExcelUtils;
import com.kushui.springbootinit.utils.SqlUtils;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 帖子接口
 *
 * @author kushui
 * *
 */
@RestController
@RequestMapping("/chart")
@Slf4j
public class ChartController {

    @Resource
    private ChartService chartService;

    @Resource
    private UserService userService;

    @Resource
    private AiManager aiManager;

    @Resource
    private RedisLimiterManager redisLimiterManager;

    @Resource
    private ThreadPoolExecutor threadPoolExecutor;

    @Resource
    private BiMessageProducer biMessageProducer;

    @Resource
    private Chart_UserIdMapper chartUserIdMapper;

    @Resource
    private StringRedisTemplate redisTemplate;

    @Resource
    private ChartMapper chartMapper;

    private final static Gson GSON = new Gson();

    //自定义封装的线程池名，轮询线程池数，线程池核心数，线程池最大数，阻塞队列容量，线程睡眠10毫秒
    private final static Executor backPressureExecutor = new BackPressureExecutor("asyncExecutor",2,2,1024,1024,10L);

    // region 增删改查

    /**
     * 创建
     *
     * @param chartAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Long> addChart(@RequestBody ChartAddRequest chartAddRequest, HttpServletRequest request) {
        if (chartAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartAddRequest, chart);

        User loginUser = userService.getLoginUser(request);
        chart.setUserId(loginUser.getId());

        boolean result = chartService.save(chart);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        long newChartId = chart.getId();
        return ResultUtils.success(newChartId);
    }

    /**
     * 删除
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteChart(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldChart.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean b = chartService.removeById(id);
        return ResultUtils.success(b);
    }

    /**
     * 更新（仅管理员）
     *
     * @param chartUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateChart(@RequestBody ChartUpdateRequest chartUpdateRequest) {
        if (chartUpdateRequest == null || chartUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartUpdateRequest, chart);

        long id = chartUpdateRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = chartService.updateById(chart);
        return ResultUtils.success(result);
    }

    /**
     * 根据 id 获取
     *
     * @param id
     * @return
     */
    @GetMapping("/get")
    public BaseResponse<Chart> getChartById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = chartService.getById(id);
        if (chart == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        return ResultUtils.success(chart);
    }

    /**
     * 分页获取列表（封装类）
     *
     * @param chartQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page")
    public BaseResponse<Page<Chart>> listChartByPage(@RequestBody ChartQueryRequest chartQueryRequest,
                                                     HttpServletRequest request) {
        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Chart> chartPage = chartService.page(new Page<>(current, size),
                getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartPage);
    }

    /**
     * 分页获取当前用户创建的资源列表
     *
     * @param chartQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page")
    public BaseResponse<Page<Chart>> listMyChartByPage(@RequestBody ChartQueryRequest chartQueryRequest,
                                                       HttpServletRequest request) {
        if (chartQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        chartQueryRequest.setUserId(loginUser.getId());
        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        //从redis中查询数据,current-1是因为前端参数有问题
        List<Long> idList = chartMapper.getIdWithPageAndUser(current-1L, size,loginUser.getId());
        ArrayList<Chart> charts = new ArrayList<>();
        for (Long id :idList) {
            String key = ChartConstant.REDIS_CACHE_CHART +loginUser.getId()+"_"+id;
            System.out.println("==============查询缓存============="+id);
            String chartJSON = redisTemplate.opsForValue().get(key);
            Chart chart ;
            if (StringUtils.isBlank(chartJSON)){
                System.out.println("==============缓存中没有内容，查询数据库=============");
                 chart = chartService.getById(id);
                ThrowUtils.throwIf(chart == null, ErrorCode.NOT_FOUND_ERROR);
                //将查询到的缓存放到数据库中
                redisTemplate.opsForValue().set(key,JSONObject.toJSONString(chart),60,TimeUnit.SECONDS);
            }else {
                 chart = JSONUtil.toBean(chartJSON, Chart.class);
            }
            charts.add(chart);
        }
        Page<Chart> chartPage = new Page<>();
        chartPage.setRecords(charts);
        chartPage.setPages(current);
        chartPage.setSize(size);


//        Page<Chart> chartPage = chartService.page(new Page<>(current, size),
//                getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartPage);
    }

    // endregion


    /**
     * 编辑（用户）
     *
     * @param chartEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editChart(@RequestBody ChartEditRequest chartEditRequest, HttpServletRequest request) {
        if (chartEditRequest == null || chartEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartEditRequest, chart);

        User loginUser = userService.getLoginUser(request);
        long id = chartEditRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldChart.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = chartService.updateById(chart);
        return ResultUtils.success(result);
    }


    private QueryWrapper<Chart> getQueryWrapper(ChartQueryRequest chartQueryRequest) {
        QueryWrapper<Chart> queryWrapper = new QueryWrapper<>();
        if (chartQueryRequest == null) {
            return queryWrapper;
        }
        Long id = chartQueryRequest.getId();
        String name = chartQueryRequest.getName();
        String goal = chartQueryRequest.getGoal();
        String chartType = chartQueryRequest.getChartType();
        Long userId = chartQueryRequest.getUserId();
        String sortField = chartQueryRequest.getSortField();
        String sortOrder = chartQueryRequest.getSortOrder();

        queryWrapper.eq(id != null && id > 0, "id", id);
        queryWrapper.like(StringUtils.isNotBlank(name), "name", name);
        queryWrapper.eq(StringUtils.isNotBlank(goal), "goal", goal);
        queryWrapper.eq(StringUtils.isNotBlank(chartType), "chartType", chartType);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq("isDelete", false);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }

    /**
     * 智能分析（同步）
     *
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    @PostMapping("/gen")
    public BaseResponse<BiResponse> genChartByAi(@RequestPart("file") MultipartFile multipartFile,
                                                 GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {
        String name = genChartByAiRequest.getName();
        String goal = genChartByAiRequest.getGoal();
        String chartType = genChartByAiRequest.getChartType();
        // 校验
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        ThrowUtils.throwIf(StringUtils.isNotBlank(name) && name.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");
        // 校验文件
        long size = multipartFile.getSize();
        String originalFilename = multipartFile.getOriginalFilename();
        // 校验文件大小
        final long ONE_MB = 1024 * 1024L;
        ThrowUtils.throwIf(size > ONE_MB, ErrorCode.PARAMS_ERROR, "文件超过 1M");
        // 校验文件后缀 aaa.png
        String suffix = FileUtil.getSuffix(originalFilename);
        final List<String> validFileSuffixList = Arrays.asList("xlsx");
        ThrowUtils.throwIf(!validFileSuffixList.contains(suffix), ErrorCode.PARAMS_ERROR, "文件后缀非法");
        //从request中快速获取用户信息
        User loginUser = userService.getLoginUser(request);
        // 限流判断，每个用户一个限流器
        redisLimiterManager.doRateLimit("genChartByAi_" + loginUser.getId());
        // 无需写 prompt，直接调用现有模型，https://www.yucongming.com，公众号搜【鱼聪明AI】
//        final String prompt = "你是一个数据分析师和前端开发专家，接下来我会按照以下固定格式给你提供内容：\n" +
//                "分析需求：\n" +
//                "{数据分析的需求或者目标}\n" +
//                "原始数据：\n" +
//                "{csv格式的原始数据，用,作为分隔符}\n" +
//                "请根据这两部分内容，按照以下指定格式生成内容（此外不要输出任何多余的开头、结尾、注释）\n" +
//                "【【【【【\n" +
//                "{前端 Echarts V5 的 option 配置对象js代码，合理地将数据进行可视化，不要生成任何多余的内容，比如注释}\n" +
//                "【【【【【\n" +
//                "{明确的数据分析结论、越详细越好，不要生成多余的注释}";
        long biModelId = CommonConstant.BI_MODEL_ID;
        // 分析需求：
        // 分析网站用户的增长情况
        // 原始数据：
        // 日期,用户数
        // 1号,10
        // 2号,20
        // 3号,30

        // 构造用户输入
        StringBuilder userInput = new StringBuilder();
        userInput.append("分析需求：").append("\n");

        // 拼接分析目标
        String userGoal = goal;
        if (StringUtils.isNotBlank(chartType)) {
            userGoal += "，请使用" + chartType;
        }
        userInput.append(userGoal).append("\n");
        userInput.append("原始数据：").append("\n");
        // 压缩后的数据
        String csvData = ExcelUtils.excelToCsv(multipartFile);
        userInput.append(csvData).append("\n");

        String result = aiManager.doChat(biModelId, userInput.toString());
        String[] splits = result.split("【【【【【");
        if (splits.length < 3) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 生成错误");
        }
        String genChart = splits[1].trim();//trim去掉多余的换行
        String genResult = splits[2].trim();
        // 插入到数据库
        Chart chart = new Chart();
        Chart_UserId chartUserId = new Chart_UserId();
        //为Chart_UserId生成雪花id
        Snowflake snowflake = IdUtil.createSnowflake(1, 1);
        long id = snowflake.nextId();


        System.out.println("==================" + chart.getUserId() + "=================");


        chart.setName(name);
        chart.setGoal(goal);
        //这里不再保存csvData，改为保存chartUserId
        chart.setChartData(String.valueOf(id));
        chart.setChartType(chartType);
        chart.setGenChart(genChart);
        chart.setGenResult(genResult);
        chart.setUserId(loginUser.getId());
        chart.setStatus("succeed");
        boolean saveResult = chartService.save(chart);
        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "图表保存失败");

        //保存到Chart_UserId的表中
        System.out.println("==================" + chart.getUserId() + "=================");
        chartUserId.setId(id);
        chartUserId.setChartData(csvData);
        chartUserIdMapper.insertChartData(chart.getUserId(), chartUserId);


        BiResponse biResponse = new BiResponse();
        biResponse.setGenChart(genChart);
        biResponse.setGenResult(genResult);
        biResponse.setChartId(chart.getId());

        //将数据存放到redis中
        String key = ChartConstant.REDIS_CACHE_CHART +chart.getUserId()+"_"+chart.getId();
        redisTemplate.opsForValue().set(key,JSONObject.toJSONString(chart),60,TimeUnit.SECONDS);



        return ResultUtils.success(biResponse);
    }

    /**
     * 智能分析（异步）
     *
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    @PostMapping("/gen/async")
    public BaseResponse<BiResponse> genChartByAiAsync(@RequestPart("file") MultipartFile multipartFile,
                                                      GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {

        String name = genChartByAiRequest.getName();
        String goal = genChartByAiRequest.getGoal();
        String chartType = genChartByAiRequest.getChartType();
        // 校验
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        ThrowUtils.throwIf(StringUtils.isNotBlank(name) && name.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");
        // 校验文件
        long size = multipartFile.getSize();
        String originalFilename = multipartFile.getOriginalFilename();
        // 校验文件大小
        final long ONE_MB = 1024 * 1024L;
        ThrowUtils.throwIf(size > ONE_MB, ErrorCode.PARAMS_ERROR, "文件超过 1M");
        // 校验文件后缀 aaa.png
        String suffix = FileUtil.getSuffix(originalFilename);
        final List<String> validFileSuffixList = Arrays.asList("xlsx", "xls");
        ThrowUtils.throwIf(!validFileSuffixList.contains(suffix), ErrorCode.PARAMS_ERROR, "文件后缀非法");

        User loginUser = userService.getLoginUser(request);
        // 限流判断，每个用户一个限流器
        redisLimiterManager.doRateLimit("genChartByAi_" + loginUser.getId());
        // 无需写 prompt，直接调用现有模型，https://www.yucongming.com，公众号搜【鱼聪明AI】
//        final String prompt = "你是一个数据分析师和前端开发专家，接下来我会按照以下固定格式给你提供内容：\n" +
//                "分析需求：\n" +
//                "{数据分析的需求或者目标}\n" +
//                "原始数据：\n" +
//                "{csv格式的原始数据，用,作为分隔符}\n" +
//                "请根据这两部分内容，按照以下指定格式生成内容（此外不要输出任何多余的开头、结尾、注释）\n" +
//                "【【【【【\n" +
//                "{前端 Echarts V5 的 option 配置对象js代码，合理地将数据进行可视化，不要生成任何多余的内容，比如注释}\n" +
//                "【【【【【\n" +
//                "{明确的数据分析结论、越详细越好，不要生成多余的注释}";
        long biModelId = 1659171950288818178L;
        // 分析需求：
        // 分析网站用户的增长情况
        // 原始数据：
        // 日期,用户数
        // 1号,10
        // 2号,20
        // 3号,30

        // 构造用户输入
        StringBuilder userInput = new StringBuilder();
        userInput.append("分析需求：").append("\n");

        // 拼接分析目标
        String userGoal = goal;
        if (StringUtils.isNotBlank(chartType)) {
            userGoal += "，请使用" + chartType;
        }
        userInput.append(userGoal).append("\n");
        userInput.append("原始数据：").append("\n");
        // 压缩后的数据
        String csvData = ExcelUtils.excelToCsv(multipartFile);
        userInput.append(csvData).append("\n");

        // 插入到数据库
        Chart chart = new Chart();
        chart.setName(name);
        chart.setGoal(goal);
        chart.setChartData(csvData);
        chart.setChartType(chartType);
        chart.setStatus("wait");
        chart.setUserId(loginUser.getId());
        //先将要查询的数据放入数据库然后在启动异步线程调用ai分析，但是调用第三方ai并没有返回是否成功和失败，消息丢失了
        //所以要引入消息队列，当消息丢失，会再次向第三方发送请求，例如表中第一行数据，发送异步请求，却没有日志信息
        boolean saveResult = chartService.save(chart);
        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "图表保存失败");

        // todo 建议处理任务队列满了后，抛异常的情况
        //该异步线程不返回值，而是不断改变chart状态，并将最终结果保存到数据库
//        CompletableFuture.runAsync(() -> {
//
//            // 定义任务重试器
//            Retryer<Boolean> retryer = RetryerBuilder.<Boolean>newBuilder()
//                    .retryIfResult(Predicates.<Boolean>isNull()) // 如果结果为空则重试
//                    .retryIfResult(Predicates.equalTo(false))//结果为false时重试
//                    .retryIfExceptionOfType(IOException.class) // 发生IO异常则重试
//                    .retryIfRuntimeException() // 发生运行时异常则重试
//                    //重试后，出现异常待5s后重试，再出现异常，等待10s后重试
//                    .withWaitStrategy(WaitStrategies.incrementingWait(5, TimeUnit.SECONDS, 5, TimeUnit.SECONDS)) // 等待
//                    .withStopStrategy(StopStrategies.stopAfterAttempt(3)) // 允许执行4次（首次执行 + 最多重试3次）
//                    .withRetryListener(new MyRetryListener())
//                    .build();
//
//            // 先修改图表任务状态为 “执行中”。等执行成功后，修改为 “已完成”、保存执行结果；执行失败后，状态修改为 “失败”，记录任务失败信息。
//            Chart updateChart = new Chart();
//            updateChart.setId(chart.getId());
//            updateChart.setStatus("running");
//            boolean b = chartService.updateById(updateChart);
//            if (!b) {
//                handleChartUpdateError(chart.getId(), "更新图表执行中状态失败");
//                return;
//            }
//
//            Callable<Boolean> callable = () -> {
//                //调用第三方接口AI
//                String result = aiManager.doChat(biModelId, userInput.toString());
//                String[] splits = result.split("【【【【【");
//                if (splits.length < 3) {
//                    handleChartUpdateError(chart.getId(), "AI 生成错误");
//                    return false;
//                }
//                String genChart = splits[1].trim();
//                String genResult = splits[2].trim();
//                if (StringUtils.isBlank(genChart) || StringUtils.isBlank(genResult)){
//                    return false;
//                }
//                Chart updateChartResult = new Chart();
//                updateChartResult.setId(chart.getId());
//                updateChartResult.setGenChart(genChart);
//                updateChartResult.setGenResult(genResult);
//                // todo 建议定义状态为枚举值
//                updateChartResult.setStatus("succeed");
//                boolean updateResult = chartService.updateById(updateChartResult);
//                if (!updateResult) {
//                    handleChartUpdateError(chart.getId(), "更新图表succeed状态失败");
//                    return false;
//                }
//
//                return true;
//            };
//            try {
//                retryer.call(callable); // 执行
//            } catch (RetryException | ExecutionException e) { // 重试次数超过阈值或被强制中断
//                e.printStackTrace();
//            }
//
//        }, threadPoolExecutor);
        chartService.genChartByAiAsync(chart, userInput);


        BiResponse biResponse = new BiResponse();
        biResponse.setChartId(chart.getId());
        return ResultUtils.success(biResponse);
    }

    /**
     * 智能分析（异步消息队列）
     *
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    @PostMapping("/gen/async/mq")
    public BaseResponse<BiResponse> genChartByAiAsyncMq(@RequestPart("file") MultipartFile multipartFile,
                                                        GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {
        String name = genChartByAiRequest.getName();
        String goal = genChartByAiRequest.getGoal();
        String chartType = genChartByAiRequest.getChartType();
        // 校验
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        ThrowUtils.throwIf(StringUtils.isNotBlank(name) && name.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");
        // 校验文件
        long size = multipartFile.getSize();
        String originalFilename = multipartFile.getOriginalFilename();
        // 校验文件大小
        final long ONE_MB = 1024 * 1024L;
        ThrowUtils.throwIf(size > ONE_MB, ErrorCode.PARAMS_ERROR, "文件超过 1M");
        // 校验文件后缀 aaa.png
        String suffix = FileUtil.getSuffix(originalFilename);
        final List<String> validFileSuffixList = Arrays.asList("xlsx", "xls");
        ThrowUtils.throwIf(!validFileSuffixList.contains(suffix), ErrorCode.PARAMS_ERROR, "文件后缀非法");

        User loginUser = userService.getLoginUser(request);
        // 限流判断，每个用户一个限流器
        redisLimiterManager.doRateLimit("genChartByAi_" + loginUser.getId());
        // 无需写 prompt，直接调用现有模型，https://www.yucongming.com，公众号搜【鱼聪明AI】
//        final String prompt = "你是一个数据分析师和前端开发专家，接下来我会按照以下固定格式给你提供内容：\n" +
//                "分析需求：\n" +
//                "{数据分析的需求或者目标}\n" +
//                "原始数据：\n" +
//                "{csv格式的原始数据，用,作为分隔符}\n" +
//                "请根据这两部分内容，按照以下指定格式生成内容（此外不要输出任何多余的开头、结尾、注释）\n" +
//                "【【【【【\n" +
//                "{前端 Echarts V5 的 option 配置对象js代码，合理地将数据进行可视化，不要生成任何多余的内容，比如注释}\n" +
//                "【【【【【\n" +
//                "{明确的数据分析结论、越详细越好，不要生成多余的注释}";
        long biModelId = 1659171950288818178L;
        // 分析需求：
        // 分析网站用户的增长情况
        // 原始数据：
        // 日期,用户数
        // 1号,10
        // 2号,20
        // 3号,30

        // 构造用户输入
        StringBuilder userInput = new StringBuilder();
        userInput.append("分析需求：").append("\n");

        // 拼接分析目标
        String userGoal = goal;
        if (StringUtils.isNotBlank(chartType)) {
            userGoal += "，请使用" + chartType;
        }
        userInput.append(userGoal).append("\n");
        userInput.append("原始数据：").append("\n");
        // 压缩后的数据
        String csvData = ExcelUtils.excelToCsv(multipartFile);
        userInput.append(csvData).append("\n");

        // 插入到数据库
        Chart chart = new Chart();
        chart.setName(name);
        chart.setGoal(goal);
        chart.setChartData(csvData);
        chart.setChartType(chartType);
        chart.setStatus("wait");
        chart.setUserId(loginUser.getId());
        boolean saveResult = chartService.save(chart);
        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "图表保存失败");
        long newChartId = chart.getId();
        biMessageProducer.sendMessage(String.valueOf(newChartId));
        BiResponse biResponse = new BiResponse();
        biResponse.setChartId(newChartId);
        return ResultUtils.success(biResponse);
    }


    /**
     * 智能分析（背压异步）
     *
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    @PostMapping("/gen/async/backpressure")
    public BaseResponse<BiResponse> genChartByAiAsyncBackPressure(@RequestPart("file") MultipartFile multipartFile,
                                                      GenChartByAiRequest genChartByAiRequest, HttpServletRequest request){
        String name = genChartByAiRequest.getName();
        String goal = genChartByAiRequest.getGoal();
        String chartType = genChartByAiRequest.getChartType();
        // 校验
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        ThrowUtils.throwIf(StringUtils.isNotBlank(name) && name.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");
        // 校验文件
        long size = multipartFile.getSize();
        String originalFilename = multipartFile.getOriginalFilename();
        // 校验文件大小
        final long ONE_MB = 1024 * 1024L;
        ThrowUtils.throwIf(size > ONE_MB, ErrorCode.PARAMS_ERROR, "文件超过 1M");
        // 校验文件后缀 aaa.png
        String suffix = FileUtil.getSuffix(originalFilename);
        final List<String> validFileSuffixList = Arrays.asList("xlsx", "xls");
        ThrowUtils.throwIf(!validFileSuffixList.contains(suffix), ErrorCode.PARAMS_ERROR, "文件后缀非法");

        User loginUser = userService.getLoginUser(request);
        // 限流判断，每个用户一个限流器
        redisLimiterManager.doRateLimit("genChartByAi_" + loginUser.getId());
        // 无需写 prompt，直接调用现有模型，https://www.yucongming.com，公众号搜【鱼聪明AI】
//        final String prompt = "你是一个数据分析师和前端开发专家，接下来我会按照以下固定格式给你提供内容：\n" +
//                "分析需求：\n" +
//                "{数据分析的需求或者目标}\n" +
//                "原始数据：\n" +
//                "{csv格式的原始数据，用,作为分隔符}\n" +
//                "请根据这两部分内容，按照以下指定格式生成内容（此外不要输出任何多余的开头、结尾、注释）\n" +
//                "【【【【【\n" +
//                "{前端 Echarts V5 的 option 配置对象js代码，合理地将数据进行可视化，不要生成任何多余的内容，比如注释}\n" +
//                "【【【【【\n" +
//                "{明确的数据分析结论、越详细越好，不要生成多余的注释}";
        long biModelId = 1659171950288818178L;
        // 分析需求：
        // 分析网站用户的增长情况
        // 原始数据：
        // 日期,用户数
        // 1号,10
        // 2号,20
        // 3号,30

        // 构造用户输入
        StringBuilder userInput = new StringBuilder();
        userInput.append("分析需求：").append("\n");

        // 拼接分析目标
        String userGoal = goal;
        if (StringUtils.isNotBlank(chartType)) {
            userGoal += "，请使用" + chartType;
        }
        userInput.append(userGoal).append("\n");
        userInput.append("原始数据：").append("\n");
        // 压缩后的数据
        String csvData = ExcelUtils.excelToCsv(multipartFile);
        userInput.append(csvData).append("\n");

        // 插入到数据库
        Chart chart = new Chart();
        chart.setName(name);
        chart.setGoal(goal);
        chart.setChartData(csvData);
        chart.setChartType(chartType);
        chart.setStatus("wait");
        chart.setUserId(loginUser.getId());
        //先将要查询的数据放入数据库然后在启动异步线程调用ai分析，但是调用第三方ai并没有返回是否成功和失败，消息丢失了
        //所以要引入消息队列，当消息丢失，会再次向第三方发送请求，例如表中第一行数据，发送异步请求，却没有日志信息
        boolean saveResult = chartService.save(chart);
        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "图表保存失败");

        //该异步线程不返回值，而是不断改变chart状态，并将最终结果保存到数据库
        CompletableFuture.runAsync(() -> {

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

            // 先修改图表任务状态为 “执行中”。等执行成功后，修改为 “已完成”、保存执行结果；执行失败后，状态修改为 “失败”，记录任务失败信息。
            Chart updateChart = new Chart();
            updateChart.setId(chart.getId());
            updateChart.setStatus("running");
            boolean b = chartService.updateById(updateChart);
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
                boolean updateResult = chartService.updateById(updateChartResult);
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

        }, backPressureExecutor);
        chartService.genChartByAiAsync(chart, userInput);


        BiResponse biResponse = new BiResponse();
        biResponse.setChartId(chart.getId());
        return ResultUtils.success(biResponse);

    }

    private void handleChartUpdateError(long chartId, String execMessage) {
        Chart updateChartResult = new Chart();
        updateChartResult.setId(chartId);
        updateChartResult.setStatus("failed");
        updateChartResult.setExecMessage(execMessage);
        boolean updateResult = chartService.updateById(updateChartResult);
        if (!updateResult) {
            log.error("更新图表失败状态失败" + chartId + "," + execMessage);
        }
    }

}
