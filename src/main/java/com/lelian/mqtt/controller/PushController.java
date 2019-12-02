package com.lelian.mqtt.controller;

import com.alibaba.fastjson.JSONObject;
import com.lelian.mqtt.common.Result;
import com.lelian.mqtt.service.PushCallback;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;


import java.util.Date;

@Controller
@RequestMapping(value="/mqtt")
public class PushController {

    // tcp://MQTT安装的服务器地址:MQTT定义的端口号
    public static final String HOST = "tcp://127.0.0.1:1883";
    // 定义一个主题
    //public static final String TOPIC = "IM/admin/Inbox";
    // 定义MQTT的ID，可以在MQTT服务配置中指定
    private static final String clientid = "server11";

    private static MqttClient client;
    private static MqttTopic topicCustom;
    private String userName = "admin";
    private String passWord = "admin";

    private MqttMessage message;

    /**
     *  构造函数
     *
     * @throws MqttException
     */
    public PushController() throws MqttException {
        // MemoryPersistence设置clientid的保存形式，默认为以内存保存
        client = new MqttClient(HOST, clientid, new MemoryPersistence());
        connect();
    }

    /**
     * 用来连接服务器
     */
    private void connect() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(false);
        //options.setUserName(userName);
        //options.setPassword(passWord.toCharArray());
        // 设置超时时间
        options.setConnectionTimeout(10);
        // 设置会话心跳时间
        options.setKeepAliveInterval(20);
        try {
            client.setCallback(new PushCallback());
            client.connect(options);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     *
     * @param topic
     * @param message
     * @throws MqttPersistenceException
     * @throws MqttException
     */
    public void publish(MqttTopic topic, MqttMessage message) throws MqttPersistenceException, MqttException {
        MqttDeliveryToken token = topic.publish(message);
        token.waitForCompletion();
        System.out.println("message is published completely! " + token.isComplete());
    }

    /**
     * 保存
     */
    @RequestMapping(value="/save", produces = "application/json;charset=utf-8")
    @ResponseBody
    public Result save(){
        Result result = new Result();
        try {
            //创建MQTTserver
            PushController server = new PushController();
            server.message = new MqttMessage();
            server.message.setQos(2);
            server.message.setRetained(false);
            //拼接要传入的json字符串
            JSONObject jsonObj = new JSONObject();
            jsonObj.put("title", "测试推送");
            jsonObj.put("time", new Date().getTime());
            //放入mqtt server  转utf-8防止乱码
            server.message.setPayload(jsonObj.toString().getBytes("UTF-8"));

//            for(int i=0;i<10;i++) {
//                //自定义topic 设置topic
//                topicCustom = client.getTopic("MQTT_TOPIC_"+i);
//                //推送
//                server.publish(server.topicCustom, server.message);
//                System.out.println(server.message.isRetained() + "------ratained状态");
//            }
            //自定义topic 设置topic
            topicCustom = client.getTopic("tp1");
            //推送
            server.publish(server.topicCustom, server.message);
            System.out.println(server.message.isRetained() + "------ratained状态");

            result.setMsg("success");
        } catch (Exception e) {
            e.printStackTrace();
            result.fail().setMsg("fail");
        }
        return result;
    }

}
