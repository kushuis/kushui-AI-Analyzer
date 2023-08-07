package com.kushui.springbootinit.manager;

import com.kushui.springbootinit.common.ErrorCode;
import com.kushui.springbootinit.exception.BusinessException;
import com.yupi.yucongming.dev.client.YuCongMingClient;
import com.yupi.yucongming.dev.common.BaseResponse;
import com.yupi.yucongming.dev.model.DevChatRequest;
import com.yupi.yucongming.dev.model.DevChatResponse;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 用于对接 AI 平台
 */
@Service
public class AiManager {

    @Resource
    private YuCongMingClient yuCongMingClient;


    private BaseResponse<DevChatResponse> response;

    /**
     * AI 对话
     *
     * @param modelId
     * @param message
     * @return
     */
    public String doChat(long modelId, String message) {
//        System.out.println("==============进入异常=================");
//        try {
//            String str = null;
//            str.length();
//        }catch (Exception e){
//            throw new RuntimeException(e);
//        }
//        System.out.println("=================进入睡眠=================");
//        try {
//            Thread.sleep(15000);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }

        //        Flux<DevChatRequest> devChatRequestFlux= Flux.just(devChatRequest);
//                  devChatRequestFlux
//                .delayElements(Duration.ofMillis(10))
//                .onBackpressureBuffer(50)
//                .delayElements(Duration.ofMillis(100))
//                .subscribe(
//                        item -> {
//                            this.response = response;},
//
//                        ex -> System.out.println("onError: "+ex),
//                        () -> System.out.println("=============onComplete===============")
//                );


        DevChatRequest devChatRequest = new DevChatRequest();
        devChatRequest.setModelId(modelId);
        devChatRequest.setMessage(message);
        //使用reactor实现背压
        BaseResponse<DevChatResponse> response = yuCongMingClient.doChat(devChatRequest);


     //   BaseResponse<DevChatResponse> response = yuCongMingClient.doChat(devChatRequest);
        if (response == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 响应错误");
        }
        return response.getData().getContent();
    }
}
