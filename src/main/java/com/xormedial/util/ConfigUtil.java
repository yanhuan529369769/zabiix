package com.xormedial.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource("classpath:config/dashboard.properties")
public class ConfigUtil {

    public static String ZABBIX_URL = "http://127.0.0.1/zabbix/api_jsonrpc.php";
//    public static String ZABBIX_URL = "http://10.50.4.59/zabbix/api_jsonrpc.php";

/*    public static String OID_404ERROR = ".1.3.6.1.4.1.22839.4.1.1000.2.1.98";
    public static String OID_454ERROR = ".1.3.6.1.4.1.22839.4.1.1000.2.1.99";
    public static String OID_455ERROR = ".1.3.6.1.4.1.22839.4.1.1000.2.1.100";
    public static String OID_OTHER_ERRORS = ".1.3.6.1.4.1.22839.4.1.1000.2.1.101";
    public static String OID_PARAMETER_COUNTS = ".1.3.6.1.4.1.22839.4.1.1000.2.1.87";
    public static String OID_PAUSE_COUNTS = ".1.3.6.1.4.1.22839.4.1.1000.2.1.85";
    public static String OID_PLAY_500ERROR = ".1.3.6.1.4.1.22839.4.1.1000.2.1.89";
    public static String OID_PLAY_503ERROR = ".1.3.6.1.4.1.22839.4.1.1000.2.1.94";
    public static String OID_PLAY_COUNTS = "1.3.6.1.4.1.22839.4.1.1000.2.1.84";
    public static String OID_SETUP_500ERROR = "1.3.6.1.4.1.22839.4.1.1000.2.1.88";
    public static String OID_SETUP_503ERROR = "1.3.6.1.4.1.22839.4.1.1000.2.1.93";
    public static String OID_SETUP_COUNTS = "1.3.6.1.4.1.22839.4.1.1000.2.1.83";
    public static String OID_TEARDOWN_COUNTS = "1.3.6.1.4.1.22839.4.1.1000.2.1.86";*/

    public static int DEF_WIDTH=4;
    public static int DEF_HEIGHT=5;



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
