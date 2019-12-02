package com.lelian.mqtt.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.lelian.mqtt.util.Constant;
import com.lelian.mqtt.util.HttpUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class ServiceApi {

    private static Logger logger = LoggerFactory.getLogger(ServiceApi.class.getName());

    //token接口
    private static final String TOKEN_API = "/api/token";

    //接口
    private static final String REALTIME_API = "/api/realTime";

    @Value("${api.appKey}")
    private String appKey;

    @Value("${api.appSecret}")
    private String appSecret;

    @Value("${api.host}")
    private String HOST;

    @Autowired
    SLRemoteService slRemoteService;

    /**
     * 定时刷新访问token
     */
    public void refreshAccessToken(){
        logger.info("刷新token开始...");
        String link = HOST+TOKEN_API;
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("appKey",appKey);
        jsonObject.put("appSecret",appSecret);
        String result = HttpUtil.post(link,jsonObject.toJSONString());
        if(StringUtils.isNotBlank(result)){
            JSONObject resultObject = JSON.parseObject(result);
            JSONObject dataObject = resultObject.getJSONObject("data");
            if(dataObject != null){
                String token = dataObject.getString("token");
                if(StringUtils.isNotBlank(token)){
                    Constant.accessToken = token;
                }
            }
        }
    }

    /**
     * 获取实时数据
     */
    public void getRealTimeData() throws IOException, InterruptedException {
        logger.info("获取实时数据...");
        int page = 1;
        int pageSize = 30;
        while (true){
            try{
                String link = HOST+REALTIME_API+"?page="+page+"&pageSize="+pageSize;
                String result = HttpUtil.get(link);
                if(StringUtils.isBlank(result)){
                    break;
                }
                JSONObject jsonObject = JSONObject.parseObject(result);
                logger.info("获取实时数据... : "+jsonObject.toJSONString());
                slRemoteService.handle(jsonObject);
                int curPage = jsonObject.getInteger("page");
                int totalPage = jsonObject.getInteger("totalPage");
                if(curPage == totalPage){
                    break;
                }else{
                    page++;
                }
                Thread.sleep(5000);
            }catch (Exception e){
                logger.error("getRealTimeData error !",e);
                break;
            }
        }
    }

}
