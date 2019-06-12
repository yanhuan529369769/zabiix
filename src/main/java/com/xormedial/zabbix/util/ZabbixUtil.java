package com.xormedial.zabbix.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.util.StringUtils;

import java.awt.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.*;

public class ZabbixUtil {
    private static String URL = "http://10.50.4.59/zabbix/api_jsonrpc.php";

    public static String AUTH = null;

    private static final String USERNAME = "Admin";

    private static final String PASSWORD = "zabbix";


    /**
     * 向Zabbix发送Post请求，并返回json格式字符串
     *
     * @return
     * @throws Exception
     */
    public static String sendPost(Map map) {
        String param = JSON.toJSONString(map);
        System.out.println(param);
        HttpURLConnection connection = null;
        DataOutputStream out = null;
        BufferedReader reader = null;
        StringBuffer sb = null;
        try {
            //创建连接
            java.net.URL url = new URL(URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Accept", "application/json"); // 设置接收数据的格式
            connection.setRequestProperty("Content-Type", "application/json"); // 设置发送数据的格式

            connection.connect();

            //POST请求
            out = new DataOutputStream(connection.getOutputStream());
            out.writeBytes(param);
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


    private static Map getRequestMap(String method, Object params) {
        Map<String, Object> map = new HashMap<>();
        map.put("jsonrpc", "2.0");
        map.put("method", method);
        map.put("auth", AUTH);
        map.put("id", 1);
        map.put("params", params);
        return map;
    }

    public static void login() {
        /**
         * 通过用户名和密码设置AUTH，获得权限 @
         */
        Map<String, Object> params = new HashMap<>();
        params.put("user", USERNAME);

        params.put("password", PASSWORD);

        Map map = getRequestMap("user.login", params);
        String response = sendPost(map);
        JSONObject json = JSON.parseObject(response);
        AUTH = json.getString("result");
    }

    static {
        login();
    }

    public static String createGraph(String name, List<String> itemIdlist, int graphtype) {
        Map<String, Object> params = new HashMap<>();
        params.put("name", name);
        params.put("width", 900);
        params.put("height", 200);
        params.put("hostids", 10390);
        List<Map<String, Object>> items = new ArrayList<>();
        for (String itemId : itemIdlist) {
            Map m = new HashMap();
            m.put("itemid", itemId);
            m.put("color", ZabbixColor.randomColor());
            items.add(m);
        }
        params.put("gitems", items);
        params.put("graphtype", graphtype);

        String s = sendPost(getRequestMap("graph.create", params));
        JSONArray graphids = JSON.parseObject(s).getJSONObject("result").getJSONArray("graphids");
        return (String) graphids.get(0);
    }

    public static String createItem(String name, String hostId, String key) {
        String str = "{\n" +
                "    \"jsonrpc\": \"2.0\",\n" +
                "    \"method\": \"item.create\",\n" +
                "    \"params\": {\n" +
                "        \"name\": \"" + name + "\",\n" +
                "        \"hostid\": \"" + hostId + "\",\n" +
                "        \"type\": 8,\n" +
                "        \"interfaceid\": \"96\",\n" +
                "        \"value_type\": 3,\n" +
                "        \"delay\": \"10\"\n" +
                "    },\n" +
                "    \"id\": 1\n" +
                "}";

        Map map = JSON.parseObject(str, Map.class);
        map.put("auth", ZabbixUtil.AUTH);
        ((Map) map.get("params")).put("key_", key);
        String s = ZabbixUtil.sendPost(map);
        JSONArray hostids = JSON.parseObject(s).getJSONObject("result").getJSONArray("itemids");
        return (String) hostids.get(0);
    }

    public static String getHostinterface(String hostid) {
        Map<String, Object> params = new HashMap<>();
        params.put("output ", "extend");
        params.put("hostids", hostid);
        String s = sendPost(getRequestMap("hostinterface.get", params));
        return JSON.parseObject(s).getJSONArray("result").getJSONObject(0).getString("interfaceid");
    }


    public static void createVirtualHost() {
        //  先把host挂到zabbix_server下，获取所有FMS500 SSS OSTR开头的group的name，去创建item
        Map<String, List<String>> groupName = getGroupName();
        List<String> fms5000 = groupName.get("FMS5000");
        List<String> fms5000GroupIds = getGroupId(fms5000);
        List<String> sss = groupName.get("SSS");
        List<String> sssGroupIds = getGroupId(sss);
        List<String> ostr = groupName.get("OSTR");
        List<String> ostrGroupIds = getGroupId(ostr);
        fms5000GroupIds.addAll(sssGroupIds);
        fms5000GroupIds.addAll(ostrGroupIds);

        String zabbix_servers_groupid = getGroupId("Zabbix servers");

        List<String> fmsIngestSessionItemIdList = new ArrayList<>();
        List<String> fmsStreamingSessionItemIdList = new ArrayList<>();

        if (fms5000 != null && fms5000.size() > 0) {
            List<String> fms5000HotsIdList = creatHost(fms5000.size(), "127.0.0.1", "XorFMS5000-", zabbix_servers_groupid);
            //  创建item
            for (int i = 0; i < fms5000HotsIdList.size(); i++) {

//                String itemId = creatItem("IngestBindWidthTotal", "grpsum[\"" + fms5000.get(i) + "\"," +
//                        "\"IngestBindWidth\",last,0]", fms5000HotsIdList.get(i));

                String ingestBindWidthTotal = createItem("IngestBindWidthTotal", fms5000HotsIdList.get(i), "grpsum" +
                        "[\"" + fms5000.get(i) + "\"," +
                        "\"IngestBindWidth\",last,0]");
                String ingestSessionTotal = createItem("IngestSessionTotal", fms5000HotsIdList.get(i),
                        "grpsum[\"" + fms5000.get(i) + "\"," +
                                "\"IngestSession\",last,0]");
                String streamingBindWidthTotal = createItem("StreamingBindWidthTotal", fms5000HotsIdList.get(i),
                        "grpsum[\"" + fms5000.get(i) + "\"," +
                                "\"StreamingBindWidth\",last,0]");
                String streamingSessionTotal = createItem("StreamingSessionTotal", fms5000HotsIdList.get(i), "grpsum" +
                        "[\"" + fms5000.get(i) + "\"," +
                        "\"StreamingSession\",last,0]");

                fmsIngestSessionItemIdList.add(ingestSessionTotal);
                fmsStreamingSessionItemIdList.add(streamingSessionTotal);
            }
        }
        if (sss != null && sss.size() > 0) {
//            creatHost(sss.size(), "127.0.0.1", "SSS", zabbix_servers_groupid);
        }
        if (ostr != null && ostr.size() > 0) {
//            creatHost(ostr.size(), "127.0.0.1", "OSTR", zabbix_servers_groupid);
        }

        //  FMS5000每个区的大区的4个item都创建好后创建graph 创建到127.0.0.1上
        String IngestSessionPie = createGraph("IngestSessionPie", fmsIngestSessionItemIdList, 2);
        String IngestSessionLine = createGraph("IngestSessionLine", fmsIngestSessionItemIdList, 0);
        String StreamingSessionPie = createGraph("StreamingSessionPie", fmsStreamingSessionItemIdList, 2);
        String StreamingSessionLine = createGraph("StreamingSessionLine", fmsStreamingSessionItemIdList, 0);

        List<Map<String, String>> garphidAndName = new ArrayList<>();
        Map temp1 = new HashMap();
        temp1.put("name", "IngestSessionPie");
        temp1.put("value", IngestSessionPie);
        garphidAndName.add(temp1);
        Map temp2 = new HashMap();
        temp2.put("name", "IngestSessionLine");
        temp2.put("value", IngestSessionLine);
        garphidAndName.add(temp2);
        Map temp3 = new HashMap();
        temp3.put("name", "StreamingSessionPie");
        temp3.put("value", StreamingSessionPie);
        garphidAndName.add(temp3);
        Map temp4 = new HashMap();
        temp4.put("name", "StreamingSessionLine");
        temp4.put("value", StreamingSessionLine);
        garphidAndName.add(temp4);

        createDashboard(fms5000GroupIds, garphidAndName);

    }


    public static void main(String[] args) {
//        getDashboard(Arrays.asList(new String[]{"24"}));
        createVirtualHost();

    }

    public static void createDashboard(List<String> groupIds, List<Map<String, String>> garphidAndName) {
      /*  Map<String, Object> params = new HashMap<>();
        params.put("name", "java create dashboard");
        List widgetsList = new ArrayList();
        params.put("widgets", widgetsList);*/
        Properties prop = new Properties();
        try {
            File file = new File("./dashboard.properties");
            FileInputStream oFile = new FileInputStream(file);
            prop.load(oFile);
            String dashboardJson = prop.getProperty("dashboardJson");
            Map map = JSON.parseObject(dashboardJson, Map.class);
            map.remove("dashboardid");
            map.remove("userid");
            map.remove("private");
            map.remove("users");
            map.remove("userGroups");
            List oldWidgets = (List) map.get("widgets");
            for (Object object : oldWidgets) {
                Map widget = (Map) object;
                widget.remove("widgetid");
                String type = (String) widget.get("type");

                if ("problems".equals(type)) {
//                    newWidgets.add(widget);
                    continue;

                }
                if ("problemsbysv".equals(type)) {
                    List fields = (List) widget.get("fields");
                    Iterator iterator = fields.iterator();
                    while (iterator.hasNext()) {
                        Map next = (Map) iterator.next();
                        if ("groupids".equals(next.get("name"))) {
                            iterator.remove();
                        }
                    }

                    for (String groupId : groupIds) {
                        Map m = new HashMap();
                        m.put("name", "groupids");
                        m.put("type", "2");
                        m.put("value", groupId);
                        fields.add(m);
                    }
                }

                if ("graph".equals(type)) {
                    String name = (String) widget.get("name");
                    List fields = (List) widget.get("fields");
                    for (Map<String, String> stringStringMap : garphidAndName) {
                        if (name != null && name.equals(stringStringMap.get("name"))) {
                            for (Object field : fields) {
                                Map fiel = (Map) field;
                                if ("graphid".equals(fiel.get("name"))) {
                                    fiel.put("value", stringStringMap.get("value"));
                                }
                            }

                        }
                    }


                }
            }
            map.put("name", "create dashboard");


            deleteDashboard("create dashboard");

            String s = sendPost(getRequestMap("dashboard.create", map));
            System.out.println(s);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getDashboard(String dashboardName) {

        Map<String, Object> params = new HashMap<>();
        params.put("output", "extend");
//        params.put("dashboardids", new ArrayList<>());

        String s = sendPost(getRequestMap("dashboard.get", params));
        JSONArray result = JSON.parseObject(s).getJSONArray("result");
        for (Object o : result) {
            JSONObject jsonObject = (JSONObject) o;
            String name = jsonObject.getString("name");
            if (name != null && name.equals(dashboardName)) {
                return jsonObject.getString("dashboardid");
            }

        }
        return null;
    }

    public static void deleteDashboard(String dashboardName) {
        List params = new ArrayList();
        params.add(getDashboard(dashboardName));
        String s = sendPost(getRequestMap("dashboard.delete", params));
        System.out.println(s);
    }

    public static void getDashboard(List groupids) {
        Map<String, Object> params = new HashMap<>();
        params.put("output", "extend");
        params.put("selectWidgets", "extend");
        params.put("selectUsers", "extend");
        params.put("selectUserGroups", "extend");
        params.put("dashboardids", groupids);
        String s = sendPost(getRequestMap("dashboard.get", params));
        JSONObject result = JSON.parseObject(s).getJSONArray("result").getJSONObject(0);
        ///保存属性到b.properties文件
        Properties prop = new Properties();
        System.out.println(ConfigUtil.getPath(ZabbixUtil.class));
        try {
            File file = new File("./dashboard.properties");
            FileOutputStream oFile = new FileOutputStream(file, false);//true表示追加打开
            prop.setProperty("dashboardJson", result.toJSONString());
            prop.store(oFile, "last dashboard json");
            oFile.close();
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    public static void getHost() {
        Map<String, Object> filter = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        params.put("output", "extend");
        List host = new ArrayList();
        host.add("FMS50001");
        filter.put("host", host);
        params.put("filter", filter);
        String s = sendPost(getRequestMap("host.get", params));
        System.out.println(s);
    }


    // 创建每个大区的抽象层,挂在zabbix_server下面
    public static List<String> creatHost(Integer hostNum, String ip, String hostBaseName, String groupId) {
        List<String> result = new ArrayList<>();
        if (hostNum != null && hostNum > 0) {
            for (int i = 0; i < hostNum; i++) {
                Map<String, Object> params = new HashMap<>();
                params.put("host", hostBaseName + (i + 1));
                List interfacesList = new ArrayList();
                Map<String, Object> interfaces = new HashMap<>();
                interfaces.put("type", 1);
                interfaces.put("main", 1);
                interfaces.put("useip", 1);
                interfaces.put("ip", ip);
                interfaces.put("dns", "");
                interfaces.put("port", "10050");
                interfacesList.add(interfaces);
                params.put("interfaces", interfacesList);

                List groupsList = new ArrayList();
                Map<String, Object> groups = new HashMap<>();
                groups.put("groupid", groupId);
                groupsList.add(groups);
                params.put("groups", groupsList);
                String s = sendPost(getRequestMap("host.create", params));
                JSONArray hostids = JSON.parseObject(s).getJSONObject("result").getJSONArray("hostids");
                String hostid = (String) hostids.get(0);
                result.add(hostid);
            }
        }
        return result;
    }

    private static Map parseJson(String s) {
        JSONArray result = JSON.parseObject(s).getJSONArray("result");
        JSONObject json = result.getJSONObject(0);
        return JSON.parseObject(json.toJSONString(), Map.class);
    }

    public static void creatGroup(List<String> groupNames) {
        if (groupNames != null && groupNames.size() > 0) {
            for (String groupName : groupNames) {
                Map<String, Object> params = new HashMap<>();
                params.put("name", groupName);
                System.out.println(sendPost(getRequestMap("hostgroup.create", params)));
            }
        }
    }

    public static List<String> getGroupId(List<String> hostnames) {

        if(hostnames == null || hostnames.size() ==0) return new ArrayList<>();

        List<String> groupids = new ArrayList<>();

        for (String hostname : hostnames) {
            String groupId = getGroupId(hostname);
            groupids.add(groupId);
        }

        return groupids;
    }

    public static String getGroupId(String hostname) {
        Map<String, Object> filter = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        params.put("output", "extend");
        List host = new ArrayList();
        host.add(hostname);
        filter.put("name", host);
        params.put("filter", filter);
        String s = sendPost(getRequestMap("hostgroup.get", params));
        Map m = parseJson(s);
        String groupid = (String) m.get("groupid");
        return groupid;
    }


    public static Map<String, List<String>> getGroupName() {
        Map<String, Object> filter = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        params.put("output", "extend");
        params.put("filter", filter);
        Map<String, Object> map = getRequestMap("hostgroup.get", params);
        String response = sendPost(map);

        List<String> fms = new ArrayList();
        List<String> sss = new ArrayList();
        List<String> ostr = new ArrayList();
        JSONArray result = JSON.parseObject(response).getJSONArray("result");
        if (result.size() > 0) {
            for (int i = 0; i < result.size(); i++) {
                JSONObject json = result.getJSONObject(i);
                Map m = (Map) JSON.parse(json.toJSONString());
                String name = (String) m.get("name");
                if (!StringUtils.isEmpty(name) && name.startsWith("XorFMS5000-")) {
                    fms.add(name);
                }
                if (!StringUtils.isEmpty(name) && name.startsWith("SSS")) {
                    sss.add(name);
                }

                if (!StringUtils.isEmpty(name) && name.startsWith("OSTR")) {
                    ostr.add(name);
                }
            }
        }
        Map<String, List<String>> resultMap = new HashMap<>();
        resultMap.put("FMS5000", fms);
//        resultMap.put("SSS", sss);   先封印
//        resultMap.put("OSTR", ostr);
        return resultMap;
    }


    public static String getURL() {
        return URL;
    }

    public static void setURL(String URL) {
        ZabbixUtil.URL = URL;
    }

    public static String getAUTH() {
        return AUTH;
    }

    public static void setAUTH(String AUTH) {
        ZabbixUtil.AUTH = AUTH;
    }

    public static String getUSERNAME() {
        return USERNAME;
    }

    public static String getPASSWORD() {
        return PASSWORD;
    }

    static class ZabbixColor {


        public static String randomColor() {
            List<Integer> random = new ArrayList();
            while (random.size() < 3) {
                int r = new Random().nextInt(256);
                random.add(r);
            }

            String hexString = colorToHexValue(new Color(random.get(0), random.get(1), random.get(2)));

            return hexString.substring(2);

        }

        private static String colorToHexValue(Color color) {
            return intToHexValue(color.getAlpha()) + intToHexValue(color.getRed()) + intToHexValue(color.getGreen()) + intToHexValue(color.getBlue());
        }

        private static String intToHexValue(int number) {
            String result = Integer.toHexString(number & 0xff);
            while (result.length() < 2) {
                result = "0" + result;
            }
            return result.toUpperCase();
        }

    }

}
