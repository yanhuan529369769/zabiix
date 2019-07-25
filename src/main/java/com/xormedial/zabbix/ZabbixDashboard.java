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


    protected Map cacheMap = new HashMap();

    private User user = new User("Admin", "zabbix");

    public ZabbixDashboard() {
        login();
    }

    protected Map getRequestMap(String method, Object params) {
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

    private List<String> strChangeList(String... itemId) {

        return Arrays.asList(itemId);
    }

    @Override
    public String createGraph(String name, List<String> itemIdlist, int graphtype, int calc_fnc) {
        Map<String, Object> params = new HashMap<>();
        params.put("name", name);
        params.put("width", 900);
        params.put("height", 200);
//        params.put("hostids", 10390);
        List<Map<String, Object>> items = new ArrayList<>();
        for (String itemId : itemIdlist) {
            Map m = new HashMap();
            m.put("itemid", itemId);
            m.put("color", ZabbixColor.randomColor());
            m.put("calc_fnc", calc_fnc);
            items.add(m);
        }
        params.put("gitems", items);
        params.put("graphtype", graphtype);

        String s = HttpUtil.sendPost(getRequestMap("graph.create", params), ConfigUtil.ZABBIX_URL);
        JSONArray graphids = JSON.parseObject(s).getJSONObject("result").getJSONArray("graphids");
        return (String) graphids.get(0);
    }

    @Override
    public String createItem(String name, String hostId, String key, String units, int type) {
        Map map = getItemMap(name, hostId, key, units, type);
        String s = HttpUtil.sendPost(map, ConfigUtil.ZABBIX_URL);
        JSONArray hostids = JSON.parseObject(s).getJSONObject("result").getJSONArray("itemids");
        return (String) hostids.get(0);
    }

    protected Map getItemMap(String name, String hostId, String key, String units, int type) {
        String str = "{\n" +
                "    \"jsonrpc\": \"2.0\",\n" +
                "    \"method\": \"item.create\",\n" +
                "    \"params\": {\n" +
                "        \"name\": \"" + name + "\",\n" +
                "        \"hostid\": \"" + hostId + "\",\n" +
                "        \"type\": " + type + ",\n" +
                "        \"interfaceid\": " + getHostinterface(hostId) + ",\n" +
                "        \"value_type\": 3,\n" +
                "        \"delay\": \"5\"\n" +
                "    },\n" +
                "    \"id\": 1\n" +
                "}";
        Map map = JSON.parseObject(str, Map.class);
        map.put("auth", user.getAuth());
        ((Map) map.get("params")).put("key_", key);
        if (!StringUtils.isEmpty(units))
            ((Map) map.get("params")).put("units", units);
        return map;
    }

    @Override
    public String createItem(String name, String hostId, String key, String units, int type, String formula) {
        Map map = getItemMap(name, hostId, key, units, type);
        Map params = ((Map) map.get("params"));
        params.put("params", formula);

        String s = HttpUtil.sendPost(map, ConfigUtil.ZABBIX_URL);

        return (String) JSON.parseObject(s).getJSONObject("result").getJSONArray("itemids").get(0);
    }

    private String getHostinterface(String hostid) {
        Map<String, Object> params = new HashMap<>();
        params.put("output ", "extend");
        params.put("hostids", hostid);
        String s = HttpUtil.sendPost(getRequestMap("hostinterface.get", params), ConfigUtil.ZABBIX_URL);
        return JSON.parseObject(s).getJSONArray("result").getJSONObject(0).getString("interfaceid");
    }

    @Override
    public void createDashboard(List<String> groupIds, List<Map<String, String>> garphidAndName, String dashboardName, boolean flag, int index) {
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
                    if (!flag) {
                        for (String groupId : groupIds) {
                            Map m = new HashMap();
                            m.put("name", "groupids");
                            m.put("type", "2");
                            m.put("value", groupId);
                            fields.add(m);
                        }
                    } else {
                        Map m = new HashMap();
                        m.put("name", "groupids");
                        m.put("type", "2");
                        m.put("value", groupIds.get(index));
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

            map.put("name", dashboardName);

            deleteDashboard(dashboardName);

            //  2019/7/25 动态添加小天山各个区的widget
            int maxX = 0;
            int maxY = 0;
            for (Object oldWidget : oldWidgets) {
                Map t = (Map) oldWidget;
                int y = Integer.parseInt((String) t.get("y"));
                if (maxY < y) maxY = y;
            }
            Map<String, Integer> xy = new HashMap();
            xy.put("x", maxX);
            xy.put("y", maxY);
            Map ostrGroupParam = (Map) cacheMap.get("ostrGroupParam");
            Set set = ostrGroupParam.keySet();
            int yFlag = 0;
            if (ostrGroupParam != null && set.size() > 0) {
                Iterator iterator = set.iterator();
                while (iterator.hasNext()) {
                    yFlag++;
                    String key = (String) iterator.next();
                    addWidget(xy, key, (Map<String, String>) ostrGroupParam.get(key), oldWidgets);


                    if (yFlag % 3 == 0)
                        xy.put("x", 0);
                    else
                        xy.put("x", maxX += ConfigUtil.DEF_WIDTH);

                    if (yFlag % 3 == 0)
                        xy.put("y", maxY += ConfigUtil.DEF_HEIGHT * 2);
                }
            }

            String s = HttpUtil.sendPost(getRequestMap("dashboard.create", map), ConfigUtil.ZABBIX_URL);
            System.out.println(s);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * @param xy
     * @param key        ostr groupName
     * @param graphId
     * @param oldWidgets
     */
    private void addWidget(Map<String, Integer> xy, String key, Map<String, String> graphId, List oldWidgets) {
        int x = xy.get("x");
        int y = xy.get("y");
        int tempY = y + ConfigUtil.DEF_HEIGHT;
        String key_10mi_total = graphId.get("key_10mi_total");
        String key_10mi_error = graphId.get("key_10mi_error");
        String name = key;
        oldWidgets.add(createWidgetMap(name.substring(name.length()-1) + "区10分钟请求数趋势", key_10mi_total, x, tempY));
        tempY += ConfigUtil.DEF_HEIGHT;
        oldWidgets.add(createWidgetMap(name.substring(name.length()-1) + "区10分钟请求错误趋势", key_10mi_error, x, tempY));


    }

    private Map createWidgetMap(String name, String graphId, int x, int y) {
        Map widget = new HashMap();
        widget.put("name", name);
        widget.put("type", "graph");
        widget.put("width", ConfigUtil.DEF_WIDTH);
        widget.put("height", ConfigUtil.DEF_HEIGHT);
        widget.put("x", x);
        widget.put("y", y);

        List<Map<String, String>> fields = new ArrayList<>();
        widget.put("fields", fields);

        Map f1 = new HashMap();
        f1.put("name", "rf_rate");
        f1.put("type", "0");
        f1.put("value", "30");
        Map f2 = new HashMap();
        f2.put("name", "graphid");
        f2.put("type", "6");
        f2.put("value", graphId);
        fields.add(f1);
        fields.add(f2);
        return widget;
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
        if (getDashboard(dashboardName) == null) return;
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
    public List<String> getHost(String groupId) {
        Map<String, Object> params = new HashMap<>();
        params.put("output", "extend");
        List hostIds = new ArrayList();
        params.put("groupids", groupId);
        String s = HttpUtil.sendPost(getRequestMap("host.get", params), ConfigUtil.ZABBIX_URL);

        JSONArray result = JSON.parseObject(s).getJSONArray("result");
        for (Object o : result) {
            JSONObject jsonObject = (JSONObject) o;
            hostIds.add(jsonObject.getString("hostid"));

        }

        return hostIds;
    }


    /**
     * 创建每个大区的抽象层,挂在zabbix_server下面
     *
     * @param hostNum
     * @param ip
     * @param groupId
     * @return
     */
    @Override
    public List<String> creatHost(List<String> hostNum, String ip, String groupId) {
        List<String> result = new ArrayList<>();
        if (hostNum != null && hostNum.size() > 0) {
            for (int i = 0; i < hostNum.size(); i++) {
                Map<String, Object> params = new HashMap<>();
                params.put("host", hostNum.get(i));
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

    protected Map parseJson(String s) {
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

                if (!StringUtils.isEmpty(name) && name.startsWith("XorOSTR-")) {
                    ostr.add(name);
                }
            }
        }
        Map<String, List<String>> resultMap = new HashMap<>();
        resultMap.put("FMS5000", fms);
//        resultMap.put("SSS", sss);   先封印
        resultMap.put("OSTR", ostr);
        return resultMap;
    }

    @SuppressWarnings("Duplicates")
    @Override
    public void createFinalDashboard() {
        //  先把host挂到zabbix_server下，获取所有FMS500 SSS OSTR开头的group的name，去创建item
        Map<String, List<String>> groupName = getHostGroupName();
        List<String> fms5000 = groupName.get("FMS5000");
        List<String> fms5000GroupIds = getHostGroupId(fms5000);
        cacheMap.put("FMS5000GroupId", fms5000GroupIds);
        cacheMap.put("FMS5000GroupName", fms5000);
        List<String> sss = groupName.get("SSS");
        List<String> sssGroupIds = getHostGroupId(sss);
        List<String> ostr = groupName.get("OSTR");
        List<String> ostrGroupIds = getHostGroupId(ostr);
        fms5000GroupIds.addAll(sssGroupIds);
        fms5000GroupIds.addAll(ostrGroupIds);

        String zabbix_servers_groupid = getHostGroupId("Zabbix servers");

        List<String> fmsIngestSessionItemIdList = new ArrayList<>();
        List<String> fmsStreamingSessionItemIdList = new ArrayList<>();
        List<String> fmsIngestBindWidthItemIdList = new ArrayList<>();
        List<String> fmsStreamingBindWidthtemIdList = new ArrayList<>();

        if (fms5000 != null && fms5000.size() > 0) {
            //  createVirtualHost

            deleteHost(fms5000);

            List<String> fms5000HotsIdList = creatHost(fms5000, "127.0.0.1", zabbix_servers_groupid);
            //  创建item
            for (int i = 0; i < fms5000HotsIdList.size(); i++) {
                String ingestBindWidthTotal = createItem("IngestBindWidth", fms5000HotsIdList.get(i), "grpsum" +
                        "[\"" + fms5000.get(i) + "\"," +
                        "\"IngestBindWidth\",last,0]", "MB/s", 8);
                String ingestSessionTotal = createItem("IngestTotal", fms5000HotsIdList.get(i),
                        "grpsum[\"" + fms5000.get(i) + "\"," +
                                "\"IngestSession\",last,0]", "", 8);
                String streamingBindWidthTotal = createItem("StreamingBindWidth", fms5000HotsIdList.get(i),
                        "grpsum[\"" + fms5000.get(i) + "\"," +
                                "\"StreamingBindWidth\",last,0]", "MB/s", 8);
                String streamingSessionTotal = createItem("StreamingTotal", fms5000HotsIdList.get(i), "grpsum" +
                        "[\"" + fms5000.get(i) + "\"," +
                        "\"StreamingSession\",last,0]", "", 8);

                fmsIngestSessionItemIdList.add(ingestSessionTotal);
                fmsIngestBindWidthItemIdList.add(ingestBindWidthTotal);
                fmsStreamingSessionItemIdList.add(streamingSessionTotal);
                fmsStreamingBindWidthtemIdList.add(streamingBindWidthTotal);
            }
        }
        if (sss != null && sss.size() > 0) {
//            creatHost(sss.size(), "127.0.0.1", "SSS", zabbix_servers_groupid);
        }
        List<String> key_10mi_res_totalItemIdList = new ArrayList<>();
        List<String> key_10mi_reserror_totalItemIdList = new ArrayList<>();
        List<String> key_10mi_setup_totalItemIdList = new ArrayList<>();
        List<String> key_10mi_setup_errorItemIdList = new ArrayList<>();
        List<String> key_10mi_play_totalItemIdList = new ArrayList<>();
        List<String> key_10mi_play_errorItemIdList = new ArrayList<>();
        List<String> key_10mi_parameter_totalItemIdList = new ArrayList<>();
        List<String> key_10mi_teardown_totalItemIdList = new ArrayList<>();
        List<String> key_10mi_404_errorItemIdList = new ArrayList<>();
        List<String> key_10mi_other_errorItemIdList = new ArrayList<>();

//        ostr.sort(Comparator.reverseOrder());
        Map temp = new LinkedHashMap();
        if (ostr != null && ostr.size() > 0) {//  小天山snmp
            deleteHost(ostr);
            List<String> ostrHotsIdList = creatHost(ostr, "127.0.0.1", zabbix_servers_groupid);
            for (int i = 0; i < ostrHotsIdList.size(); i++) {
                createItem("404errortotal", ostrHotsIdList.get(i), assembleItemKey(ostr.get(i), "404error"), "", 8);
                createItem("454errortotal", ostrHotsIdList.get(i), assembleItemKey(ostr.get(i), "454error"), "", 8);
                createItem("455errortotal", ostrHotsIdList.get(i), assembleItemKey(ostr.get(i), "455error"), "", 8);
                createItem("other_errorstotal", ostrHotsIdList.get(i), assembleItemKey(ostr.get(i), "other_errors"), "", 8);
                createItem("parameter_countstotal", ostrHotsIdList.get(i), assembleItemKey(ostr.get(i), "parameter_counts"), "", 8);
                createItem("pause_countstotal", ostrHotsIdList.get(i), assembleItemKey(ostr.get(i), "pause_counts"), "", 8);
                createItem("play_500errortotal", ostrHotsIdList.get(i), assembleItemKey(ostr.get(i), "play_500error"), "", 8);
                createItem("play_503errortotal", ostrHotsIdList.get(i), assembleItemKey(ostr.get(i), "play_503error"), "", 8);
                createItem("play_countstotal", ostrHotsIdList.get(i), assembleItemKey(ostr.get(i), "play_counts"), "", 8);
                createItem("setup_500errortotal", ostrHotsIdList.get(i), assembleItemKey(ostr.get(i), "setup_500error"), "", 8);
                createItem("setup_503errortotal", ostrHotsIdList.get(i), assembleItemKey(ostr.get(i), "setup_503error"), "", 8);
                createItem("setup_countstotal", ostrHotsIdList.get(i), assembleItemKey(ostr.get(i), "setup_counts"), "", 8);
                createItem("teardown_countstotal", ostrHotsIdList.get(i), assembleItemKey(ostr.get(i), "teardown_counts"), "", 8);


                //  setup_counts+play_counts+pause_counts+teardown_counts+parameter_counts
                String key_10mi_res_total = createItem("10分钟请求总数", ostrHotsIdList.get(i),
                        "key_10mi_res_total", "", 15, assembleItemKey2(ostr.get(i), "setup_counts", "play_counts", "pause_counts",
                                "teardown_counts", "parameter_counts"));
                //  404error+454error+455error+play_500error+play503error+setup_500error+setup_503error+other_errors
                String key_10mi_reserror_total = createItem("10分钟请求错误数", ostrHotsIdList.get(i),
                        "key_10mi_reserror_total", "", 15, assembleItemKey2(ostr.get(i), "404error", "454error", "455error", "play_500error",
                                "play_503error", "setup_500error", "setup_503error", "other_errors"));
                //  setup_counts
                String key_10mi_setup_total = createItem("10分钟SETUP", ostrHotsIdList.get(i),
                        "key_10mi_setup_total", "", 15, assembleItemKey2(ostr.get(i), "setup_counts"));

                //  setup_500error+setup_503error
                String key_10mi_setup_error = createItem("10分钟SETUP错误数", ostrHotsIdList.get(i),
                        "key_10mi_setup_error", "", 15, assembleItemKey2(ostr.get(i), "setup_500error", "setup_503error"));

                //  play_counts
                String key_10mi_play_total = createItem("10分钟PLAY", ostrHotsIdList.get(i),
                        "key_10mi_play_total", "", 15, assembleItemKey2(ostr.get(i), "play_counts"));

                //  play_500error+play503error
                String key_10mi_play_error = createItem("10分钟PLAY错误数", ostrHotsIdList.get(i),
                        "key_10mi_play_error", "", 15, assembleItemKey2(ostr.get(i), "play_500error", "play_503error"));

                //  parameter_counts
                String key_10mi_parameter_total = createItem("10分钟PARAMETER", ostrHotsIdList.get(i),
                        "key_10mi_parameter_total", "", 15, assembleItemKey2(ostr.get(i), "parameter_counts"));

                //  teardown_counts
                String key_10mi_teardown_total = createItem("10分钟TEARDOWN", ostrHotsIdList.get(i),
                        "key_10mi_teardown_total", "", 15, assembleItemKey2(ostr.get(i), "teardown_counts"));

                //  404error
                String key_10mi_404_error = createItem("10分钟404错误数", ostrHotsIdList.get(i),
                        "key_10mi_404_error", "", 15, assembleItemKey2(ostr.get(i), "404error"));

                //  454error+455error+other_errors
                String key_10mi_other_error = createItem("10分钟其他错误数", ostrHotsIdList.get(i),
                        "key_10mi_other_error", "", 15, assembleItemKey2(ostr.get(i), "454error", "455error", "other_errors"));
                key_10mi_res_totalItemIdList.add(key_10mi_res_total);
                key_10mi_reserror_totalItemIdList.add(key_10mi_reserror_total);
                key_10mi_setup_totalItemIdList.add(key_10mi_setup_total);
                key_10mi_setup_errorItemIdList.add(key_10mi_setup_error);
                key_10mi_play_totalItemIdList.add(key_10mi_play_total);
                key_10mi_play_errorItemIdList.add(key_10mi_play_error);
                key_10mi_parameter_totalItemIdList.add(key_10mi_parameter_total);
                key_10mi_teardown_totalItemIdList.add(key_10mi_teardown_total);
                key_10mi_404_errorItemIdList.add(key_10mi_404_error);
                key_10mi_other_errorItemIdList.add(key_10mi_other_error);


                String key_10mi_total = createGraph(ostr.get(i) + "key_10mi_total", strChangeList(key_10mi_setup_total, key_10mi_play_total,
                        key_10mi_parameter_total, key_10mi_teardown_total), 0, 2);

                String key_10mi_error = createGraph(ostr.get(i) + "key_10mi_error", strChangeList(key_10mi_setup_error, key_10mi_play_error,
                        key_10mi_404_error, key_10mi_other_error), 0, 2);

                Map m = new HashMap();

                m.put("key_10mi_total", key_10mi_total);
                m.put("key_10mi_error", key_10mi_error);

                temp.put(ostr.get(i), m);
            }

            cacheMap.put("ostrGroupParam", temp);
        }

        //  FMS5000每个区的大区的4个item都创建好后创建graph 创建到127.0.0.1上
        String ingestBindWidthLine = createGraph("IngestBindWidthLine", fmsIngestBindWidthItemIdList, 0, 2);
        String ingestSessionLine = createGraph("IngestSessionLine", fmsIngestSessionItemIdList, 0, 2);
        String streamingSessionPie = createGraph("StreamingSessionPie", fmsStreamingSessionItemIdList, 2, 9);
        String streamingSessionLine = createGraph("StreamingSessionLine", fmsStreamingSessionItemIdList, 0, 2);
        String streamingBindWidthPie = createGraph("StreamingBindWidthPie", fmsStreamingBindWidthtemIdList, 2, 9);
        String streamingBindWidthLine = createGraph("StreamingBindWidthLine", fmsStreamingBindWidthtemIdList, 0, 2);


        //  OSTR graph
        String key_10mi_res_totaPie = createGraph("key_10mi_res_totaPie", key_10mi_res_totalItemIdList, 2, 9);
        String key_10mi_res_totaLine = createGraph("key_10mi_res_totaLine", key_10mi_res_totalItemIdList, 0, 2);
        String key_10mi_reserror_totalLine = createGraph("key_10mi_reserror_totalLine", key_10mi_reserror_totalItemIdList, 0, 2);


        List<Map<String, String>> garphidAndName = assembleGarphidAndName(ingestBindWidthLine, ingestSessionLine, streamingSessionPie,
                streamingSessionLine, streamingBindWidthPie, streamingBindWidthLine);

        addGrapgNameInMap(garphidAndName, "10分钟请求总数", key_10mi_res_totaPie);
        addGrapgNameInMap(garphidAndName, "10分钟请求总数趋势", key_10mi_res_totaLine);
        addGrapgNameInMap(garphidAndName, "10分钟请求错误数趋势（毫秒）", key_10mi_reserror_totalLine);

        createDashboard(fms5000GroupIds, garphidAndName, "FMS5000Summary", false, 0);
    }

    protected String assembleItemKey(String groupName, String key) {
        String temp = "grpsum" + "[\"groupName\",\"key\",last,0]";
        return temp.replace("groupName", groupName).replace("key", key);
    }

    protected String assembleItemKey2(String groupName, String... key) {
        String temp = "last(\"grpsum[\\\"groupName\\\",\\\"key\\\",last,0]\")";
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < key.length; i++) {
            if (i < key.length - 1)
                sb.append(temp.replace("groupName", groupName).replace("key", key[i])).append("+");
            else
                sb.append(temp.replace("groupName", groupName).replace("key", key[i]));
        }
        return sb.toString();
    }


    protected List<Map<String, String>> assembleGarphidAndName(String ingestBindWidthLine, String ingestSessionLine, String streamingSessionPie, String streamingSessionLine, String streamingBindWidthPie, String streamingBindWidthLine) {
        List<Map<String, String>> garphidAndName = new ArrayList<>();
        addGrapgNameInMap(garphidAndName, "当前点流数", streamingSessionPie);
//        addGrapgNameInMap(garphidAndName, "\u5f53\u524d\u70b9\u6d41\u6570", streamingSessionPie);
        addGrapgNameInMap(garphidAndName, "点流数历史", streamingSessionLine);
//        addGrapgNameInMap(garphidAndName, "\u70b9\u6d41\u6570\u5386\u53f2", streamingSessionLine);
        addGrapgNameInMap(garphidAndName, "当前点流带宽（MB/s）", streamingBindWidthPie);
//        addGrapgNameInMap(garphidAndName, "\u5f53\u524d\u70b9\u6d41\u5e26\u5bbd\uff08MB/s\uff09", streamingBindWidthPie);
        addGrapgNameInMap(garphidAndName, "点流带宽历史（MB/s）", streamingBindWidthLine);
//        addGrapgNameInMap(garphidAndName, "\u70b9\u6d41\u5e26\u5bbd\u5386\u53f2\uff08MB/s\uff09", streamingBindWidthLine);
        addGrapgNameInMap(garphidAndName, "加载数历史", ingestSessionLine);
//        addGrapgNameInMap(garphidAndName, "\u52a0\u8f7d\u6570\u5386\u53f2", ingestSessionLine);
        addGrapgNameInMap(garphidAndName, "加载带宽历史（MB/s）", ingestBindWidthLine);
//        addGrapgNameInMap(garphidAndName, "\u52a0\u8f7d\u5e26\u5bbd\u5386\u53f2\uff08MB/s\uff09", ingestBindWidthLine);
        return garphidAndName;
    }

    private void addGrapgNameInMap(List<Map<String, String>> garphidAndName, String key, String value) {
        Map map = new HashMap();
        map.put("name", key);
        map.put("value", value);
        garphidAndName.add(map);
    }

    protected void deleteHost(List<String> fms5000) {
        List params = new ArrayList();

        params.addAll(getHostByName(fms5000));
        if (params.size() == 0) return;
        HttpUtil.sendPost(getRequestMap("host.delete", params), ConfigUtil.ZABBIX_URL);

    }

    public static void main(String[] args) {
//        new ZabbixDashboard().getHostByName(Arrays.asList(new String[]{"XorFMS5000-RegionA"}));
    }

    protected List<String> getHostByName(List<String> fms5000) {
        List<String> hostids = new ArrayList<>();
        for (String s : fms5000) {
            Map<String, Object> params = new HashMap<>();
            params.put("output", "hostid");
            Map<String, Object> search = new HashMap<>();
            search.put("name", s);
            params.put("search", search);
            JSONArray result = JSON.parseObject(HttpUtil.sendPost(getRequestMap("host.get", params), ConfigUtil.ZABBIX_URL)).getJSONArray("result");
            for (Object o : result) {
                JSONObject jsonObject = (JSONObject) o;
                hostids.add(jsonObject.getString("hostid"));
            }
        }
        return hostids;
    }
}
