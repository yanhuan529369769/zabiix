package com.xormedial.util;

import com.alibaba.fastjson.JSON;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

public class HttpUtil {

    /**
     * 向Zabbix发送Post请求，并返回json格式字符串
     *
     * @return
     * @throws Exception
     */
    public static String sendPost(Map map, String posturl) {
        String param = JSON.toJSONString(map);
        System.out.println(param);
        HttpURLConnection connection = null;
        DataOutputStream out = null;
        BufferedReader reader = null;
        StringBuffer sb = null;
        try {
            //创建连接
            URL url = new URL(posturl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Accept", "application/json-rpc"); // 设置接收数据的格式
            connection.setRequestProperty("Content-Type", "application/json"); // 设置发送数据的格式
//            connection.setRequestProperty("content-length", "2000"); // 设置发送数据的格式

            connection.connect();

            //POST请求
            out = new DataOutputStream(connection.getOutputStream());
            out.write(param.getBytes("UTF-8"));
            out.flush();

            //读取响应
            reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String lines;
            sb = new StringBuffer("");
            while ((lines = reader.readLine()) != null) {
                lines = new String(lines.getBytes(), "utf-8");
                sb.append(lines);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
        return sb.toString();

    }
}
