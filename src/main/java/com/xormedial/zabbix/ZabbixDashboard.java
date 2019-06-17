package com.xormedial.zabbix;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.xormedial.dashboardinterface.AbstractTopOperation;
import com.xormedial.dashboardinterface.Dashboard;
import com.xormedial.model.User;
import com.xormedial.util.ConfigUtil;
import com.xormedial.util.HttpUtil;
import org.springframework.util.StringUtils;

import java.io.*;
import java.util.List;
import java.util.*;

public class ZabbixDashboard implements AbstractTopOperation, Dashboard {

    private User user = new User("Admin", "zabbix");

    public ZabbixDashboard() {
        login();
    }

    private Map getRequestMap(String method, Object params) {
        Map<String, Object> map = new HashMap<>();
        map.put("jsonrpc", "2.0");
        map.put("method", method);
        map.put("auth", user.getAuth());
        map.put("id", 1);
        map.put("params", params);
        return map;
    }

    public void login() {
        /**
         * 通过用户名和密码设置AUTH，获得权限 @
         */
        Map<String, Object> params = new HashMap<>();

        params.put("user", user.getUserName());

        params.put("password", user.getPassword());

        Map map = getRequestMap("user.login", params);
        String response = HttpUtil.sendPost(map, ConfigUtil.ZABBIX_URL);
        JSONObject json = JSON.parseObject(response);
        String auth = json.getString("result");
        user.setAuth(auth);
    }

    @Override
    public String createGraph(String name, List<String> itemIdlist, int graphtype) {
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

        String s = HttpUtil.sendPost(getRequestMap("graph.create", params), ConfigUtil.ZABBIX_URL);
        JSONArray graphids = JSON.parseObject(s).getJSONObject("result").getJSONArray("graphids");
        return (String) graphids.get(0);
    }

    @Override
    public String createItem(String name, String hostId, String key) {
        String str = "{\n" +
                "    \"jsonrpc\": \"2.0\",\n" +
                "    \"method\": \"item.create\",\n" +
                "    \"params\": {\n" +
                "        \"name\": \"" + name + "\",\n" +
                "        \"hostid\": \"" + hostId + "\",\n" +
                "        \"type\": 8,\n" +
                "        \"interfaceid\": "+ getHostinterface(hostId)+",\n" +
                "        \"value_type\": 3,\n" +
                "        \"delay\": \"10\"\n" +
                "    },\n" +
                "    \"id\": 1\n" +
                "}";

        Map map = JSON.parseObject(str, Map.class);
        map.put("auth", user.getAuth());
        ((Map) map.get("params")).put("key_", key);
        String s = HttpUtil.sendPost(map, ConfigUtil.ZABBIX_URL);
        JSONArray hostids = JSON.parseObject(s).getJSONObject("result").getJSONArray("itemids");
        return (String) hostids.get(0);
    }

    private String getHostinterface(String hostid) {
        Map<String, Object> params = new HashMap<>();
        params.put("output ", "extend");
        params.put("hostids", hostid);
        String s = HttpUtil.sendPost(getRequestMap("hostinterface.get", params), ConfigUtil.ZABBIX_URL);
        return JSON.parseObject(s).getJSONArray("result").getJSONObject(0).getString("interfaceid");
    }

    @Override
    public void createDashboard(List<String> groupIds, List<Map<String, String>> garphidAndName, String dashboardName) {
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

            deleteDashboard(dashboardName);

            String s = HttpUtil.sendPost(getRequestMap("dashboard.create", map), ConfigUtil.ZABBIX_URL);
            System.out.println(s);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getDashboard(String dashboardName) {

        Map<String, Object> params = new HashMap<>();
        params.put("output", "extend");
//        params.put("dashboardids", new ArrayList<>());

        String s = HttpUtil.sendPost(getRequestMap("dashboard.get", params), ConfigUtil.ZABBIX_URL);
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

    @Override
    public void deleteDashboard(String dashboardName) {
        List params = new ArrayList();
        params.add(getDashboard(dashboardName));
        String s = HttpUtil.sendPost(getRequestMap("dashboard.delete", params), ConfigUtil.ZABBIX_URL);
        System.out.println(s);
    }

    public void getDashboard(List groupids) {
        Map<String, Object> params = new HashMap<>();
        params.put("output", "extend");
        params.put("selectWidgets", "extend");
        params.put("selectUsers", "extend");
        params.put("selectUserGroups", "extend");
        params.put("dashboardids", groupids);
        String s = HttpUtil.sendPost(getRequestMap("dashboard.get", params), ConfigUtil.ZABBIX_URL);
        JSONObject result = JSON.parseObject(s).getJSONArray("result").getJSONObject(0);
        ///保存属性到b.properties文件
        Properties prop = new Properties();
        System.out.println(ConfigUtil.getPath(ZabbixDashboard.class));
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

    @Override
    public String getHost() {
        Map<String, Object> filter = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        params.put("output", "extend");
        List host = new ArrayList();
        host.add("FMS50001");
        filter.put("host", host);
        params.put("filter", filter);
        String s = HttpUtil.sendPost(getRequestMap("host.get", params), ConfigUtil.ZABBIX_URL);
        System.out.println(s);
        return s;
    }


    /**
     * 创建每个大区的抽象层,挂在zabbix_server下面
     *
     * @param hostNum
     * @param ip
     * @param hostBaseName
     * @param groupId
     * @return
     */
    @Override
    public List<String> creatHost(Integer hostNum, String ip, String hostBaseName, String groupId) {
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
                String s = HttpUtil.sendPost(getRequestMap("host.create", params), ConfigUtil.ZABBIX_URL);
                JSONArray hostids = JSON.parseObject(s).getJSONObject("result").getJSONArray("hostids");
                String hostid = (String) hostids.get(0);
                result.add(hostid);
            }
        }
        return result;
    }

    private Map parseJson(String s) {
        JSONArray result = JSON.parseObject(s).getJSONArray("result");
        JSONObject json = result.getJSONObject(0);
        return JSON.parseObject(json.toJSONString(), Map.class);
    }

    public void creatGroup(List<String> groupNames) {
        if (groupNames != null && groupNames.size() > 0) {
            for (String groupName : groupNames) {
                Map<String, Object> params = new HashMap<>();
                params.put("name", groupName);
                System.out.println(HttpUtil.sendPost(getRequestMap("hostgroup.create", params), ConfigUtil.ZABBIX_URL));
            }
        }
    }

    public List<String> getHostGroupId(List<String> hostnames) {

        if (hostnames == null || hostnames.size() == 0) return new ArrayList<>();

        List<String> groupids = new ArrayList<>();

        for (String hostname : hostnames) {
            String groupId = getHostGroupId(hostname);
            groupids.add(groupId);
        }

        return groupids;
    }

    @Override
    public String getHostGroupId(String hostname) {
        Map<String, Object> filter = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        params.put("output", "extend");
        List host = new ArrayList();
        host.add(hostname);
        filter.put("name", host);
        params.put("filter", filter);
        String s = HttpUtil.sendPost(getRequestMap("hostgroup.get", params), ConfigUtil.ZABBIX_URL);
        Map m = parseJson(s);
        String groupid = (String) m.get("groupid");
        return groupid;
    }

    @Override
    public Map<String, List<String>> getHostGroupName() {
        Map<String, Object> filter = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        params.put("output", "extend");
        params.put("filter", filter);
        Map<String, Object> map = getRequestMap("hostgroup.get", params);
        String response = HttpUtil.sendPost(map, ConfigUtil.ZABBIX_URL);

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

    @Override
    public void createFinalDashboard() {
        //  先把host挂到zabbix_server下，获取所有FMS500 SSS OSTR开头的group的name，去创建item
        Map<String, List<String>> groupName = getHostGroupName();
        List<String> fms5000 = groupName.get("FMS5000");
        List<String> fms5000GroupIds = getHostGroupId(fms5000);
        List<String> sss = groupName.get("SSS");
        List<String> sssGroupIds = getHostGroupId(sss);
        List<String> ostr = groupName.get("OSTR");
        List<String> ostrGroupIds = getHostGroupId(ostr);
        fms5000GroupIds.addAll(sssGroupIds);
        fms5000GroupIds.addAll(ostrGroupIds);

        String zabbix_servers_groupid = getHostGroupId("Zabbix servers");

        List<String> fmsIngestSessionItemIdList = new ArrayList<>();
        List<String> fmsStreamingSessionItemIdList = new ArrayList<>();

        if (fms5000 != null && fms5000.size() > 0) {
            //  createVirtualHost
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

        createDashboard(fms5000GroupIds, garphidAndName, "create dashboard");

    }
}
