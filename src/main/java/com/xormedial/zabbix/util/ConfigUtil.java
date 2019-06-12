package com.xormedial.zabbix.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource("classpath:config/dashboard.properties")
public class ConfigUtil {

    @Value("${dashboardJson}")
    private  String json;

    public  String getJson() {
        return json;
    }

    public  void setJson(String json) {
        this.json = json;
    }

    /**
     * 得到某一个类的路径
     *
     * @param name
     * @return
     */
    public static String getPath(Class name) {
        String strResult = null;
        if (System.getProperty("os.name").toLowerCase().indexOf("window") > -1) {
            strResult = name.getResource("/").toString().replace("file:/", "")
                    .replace("%20", " ");
        } else {
            strResult = name.getResource("/").toString().replace("file:", "")
                    .replace("%20", " ");
        }
        return strResult;
    }

}
