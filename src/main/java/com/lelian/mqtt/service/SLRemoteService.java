package com.lelian.mqtt.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lelian.mqtt.pojo.DevicePojo;
import com.lelian.mqtt.pojo.RemoteDevicePojo;
import com.lelian.mqtt.pojo.RemoteGatewayPojo;
import com.lelian.mqtt.util.ThreadPoolUtil;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SLRemoteService {

    @Autowired
    ServiceApi serviceApi;

    @Autowired
    DataService dataService;

    private static Logger logger = LoggerFactory.getLogger(SLRemoteService.class.getName());

    /**
     * 协议-设备
     */
    public static Map<String,DevicePojo> SLMap = new HashMap<>();

    /**
     * MQTT 相关参数
     */
    private static  final String Broker = "tcp://127.0.0.1:1883";
    private static  final int Qos = 2;
    private static  final boolean isRetained = false;
    private static  final boolean isCleanSession = true;

    /**
     *  记录设备上传数据的最新时间, key: 设备序列号，value：最新时间(毫秒)
     */
    private static Map<String, Long> DeviceTimeMap = new ConcurrentHashMap<>(1024);

    /*
     * 用于保存每个设备对应的mqtt连接，key：设备序列号，value：mqtt连接
     */
    private static Map<String, MqttClient> mqttClientMap = new HashMap<>(1024);

    /**
     *  初始化SLMap
     */
    public void initSLMap(){
        try {
            String jsonString = getConfigContent();
            JSONObject devicesObject = JSONObject.parseObject(jsonString);
            if(devicesObject != null){
                JSONArray deviceArray = devicesObject.getJSONArray("devices");
                for(int i=0;i<deviceArray.size();i++){
                    String agreement = deviceArray.getString(i);
                    //网关序列号
                    String serialNumber = String.format("1200%03d", i+1);
                    int deviceNumber = 1024 * Integer.parseInt(serialNumber) + 1;
                    DevicePojo devicePojo = new DevicePojo();
                    devicePojo.setDeviceId(agreement);
                    devicePojo.setDeviceNumber(deviceNumber);
                    devicePojo.setSerialNumber(serialNumber);
                    SLMap.put(agreement, devicePojo);
                    //初始化数据库数据
                    dataService.addAgent(Integer.parseInt(serialNumber));
                    Integer modelid = dataService.addModel();
                    if(modelid != null){
                        dataService.addDevice(Integer.parseInt(serialNumber),deviceNumber,modelid);
                    }
                    dataService.addDataitem(deviceNumber);
                }
            }
        } catch (Exception e) {
            logger.error("initSLMap error",e);
        }
    }

    private static String getConfigContent() throws IOException {
        InputStream inputStream = SLRemoteService.class.getResourceAsStream("/config.json");
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder stringBuilder = new StringBuilder();
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            stringBuilder.append(line);
        }
        return stringBuilder.toString();
    }

    /**
     * MQTT发送数据
     */
    private static class MqttOperationHandler extends Thread {

        private RemoteGatewayPojo gatewayPojo;

        public MqttOperationHandler(RemoteGatewayPojo gatewayPojo) {
            this.gatewayPojo = gatewayPojo;
        }

        @Override
        public void run() {
            List<RemoteDevicePojo> remoteDevicePojos = this.gatewayPojo.getY();
            Iterator<RemoteDevicePojo> deviceItr = remoteDevicePojos.iterator();
            while (deviceItr.hasNext()) {
                RemoteDevicePojo remoteDevicePojo = deviceItr.next();
                // 移除gateway中没有数据项的设备
                List<List<Object>> items = remoteDevicePojo.getC();
                if ((items == null) || (items.size() == 0)) {
                    deviceItr.remove();
                }
            }

            if(remoteDevicePojos.size() == 0) {
                return;
            }

            RemoteGatewayPojo remoteGatewayPojo = this.gatewayPojo;

            // 序列号
            String serialNumber = remoteGatewayPojo.getZ();
            //  要发送给mqtt服务器的json数据
            String msgJson = null;
            try {
                JSONObject pojoObject = (JSONObject)JSON.toJSON(remoteGatewayPojo);
                msgJson = "[31]" + pojoObject.toJSONString();
                //msgJson = pojoObject.toJSONString();
                //logger.info("正在发送数据 : "+msgJson);
                MqttMessage mqttMessage = new MqttMessage(msgJson.getBytes());
                mqttMessage.setQos(Qos);
                mqttMessage.setRetained(isRetained);
                String topic = String.format("/at/%s/[31]", serialNumber);

                MqttClient mqttClient = SLRemoteService.mqttClientMap.get(serialNumber);
                if((mqttClient == null) || (!mqttClient.isConnected())) {
                    String clientId = "sl_" + serialNumber;
                    mqttClient = new MqttClient(Broker, clientId, new MemoryPersistence());
                    MqttConnectOptions connOpts = new MqttConnectOptions();
                    connOpts.setCleanSession(isCleanSession);
                    //connOpts.setMaxInflight(150);
                    mqttClient.connect(connOpts);
                    SLRemoteService.mqttClientMap.put(serialNumber, mqttClient);
                }

                mqttClient.publish(topic, mqttMessage);
                logger.info("serialNumber : "+serialNumber+" topic : "+topic);
                logger.info("serialNumber : "+serialNumber+" msg : "+msgJson);
                DeviceTimeMap.put(serialNumber, System.currentTimeMillis());
                logger.info("发送数据结束..");
            }catch(Exception e) {
                logger.error("mqtt error:", e);
            }

        }
    }

    public void handle(JSONObject jsonObject) throws IOException {
        JSONArray dataArray = jsonObject.getJSONArray("data");
        if(dataArray != null && !dataArray.isEmpty()){
            List<RemoteGatewayPojo> remoteGatewayPojos = parseJson(jsonObject.toJSONString());
            for (RemoteGatewayPojo remoteGatewayPojo : remoteGatewayPojos) {
                ThreadPoolUtil.executorService.execute(new MqttOperationHandler(remoteGatewayPojo));
            }
        }
    }

    /**
     * 解析从客户端传来的json数据, 并且返回指定格式的数组
     * @param jsonStr
     * @return
     */
    private List<RemoteGatewayPojo> parseJson(String jsonStr) throws IOException {

        List<RemoteGatewayPojo> gatewayPojos = new ArrayList<>();

        JSONObject jsonObject = JSONObject.parseObject(jsonStr);
        JSONArray dataArray = jsonObject.getJSONArray("data");
        for(Object object : dataArray){
            if(object instanceof JSONObject){
                JSONObject dataObject = (JSONObject)object;
                Set<String> keySet = dataObject.keySet();
                Iterator<String> iterator = keySet.iterator();
                //设备协议
                String agreement = iterator.next();
                DevicePojo devicePojo = SLMap.get(agreement);
                if(devicePojo != null){
                    String serialNumber = devicePojo.getSerialNumber();

                    // 创建RemoteGatewayPojo
                    RemoteGatewayPojo remoteGatewayPojo = new RemoteGatewayPojo();
                    gatewayPojos.add(remoteGatewayPojo);

                    remoteGatewayPojo.setZ(serialNumber);
                    List<RemoteDevicePojo> devicePojos = new ArrayList<>();
                    remoteGatewayPojo.setY(devicePojos);

                    RemoteDevicePojo remoteDevicePojo = new RemoteDevicePojo();
                    devicePojos.add(remoteDevicePojo);
                    remoteDevicePojo.setD("1");
                    remoteDevicePojo.setT((System.currentTimeMillis() + ""));
                    List<List<Object>> remoteItems = new ArrayList<>();
                    //获取统计项的值
                    JSONArray itemArray = dataObject.getJSONArray(agreement);
                    if(itemArray != null && !itemArray.isEmpty()){
                        for(int i=0;i<itemArray.size();i++){
                            JSONObject itemObject = itemArray.getJSONObject(i);
                            // 创建 List数组保存数据
                            List<Object> remoteItem  = new ArrayList<>();
                            remoteItems.add(remoteItem);
                            remoteItem.add(i+1);
                            remoteItem.add(itemObject.get("value"));
                            remoteItem.add("g");

                            //更新dataitem数据
                            //dataService.updateDataitem(devicePojo.getDeviceNumber(),itemObject.get("value")+"");
                        }
                    }
                    remoteDevicePojo.setC(remoteItems);
                }
            }
        }
        return gatewayPojos;
    }

}
