package com.xormedial.zabbix;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.xormedial.util.ConfigUtil;
import com.xormedial.util.HttpUtil;

import java.util.*;

public class ZabbixGroupHostDashboard extends ZabbixDashboard {

    @Override
    public void createFinalDashboard() {
        super.createFinalDashboard();
        //  获取每个group下的所有host，创建对应的graph
        List<String> fms5000GroupIds = (List<String>) cacheMap.get("FMS5000GroupId");
        List<String> fms5000 = (List<String>) cacheMap.get("FMS5000GroupName");
        deleteGraph();
        for (int i = 0; i < fms5000GroupIds.size(); i++) {
            List<String> host = getHost(fms5000GroupIds.get(i));
            List itemIngestSessions = new ArrayList<>();
            List itemStreamingSessions = new ArrayList<>();
            for (String s : host) {
                String itemIngestSession = getItem(s, "IngestSession", "itemid");
                String itemStreamingSession = getItem(s, "StreamingSession", "itemid");
                itemIngestSessions.add(itemIngestSession);
                itemStreamingSessions.add(itemStreamingSession);
            }

            /*String IngestSessionPie = createGraph("IngestSessionPieUnderGroup", itemIngestSessions, 2);
            String IngestSessionLine = createGraph("IngestSessionLineUnderGroup", itemIngestSessions, 0);
            String StreamingSessionPie = createGraph("StreamingSessionPieUnderGroup", itemStreamingSessions, 2);
            String StreamingSessionLine = createGraph("StreamingSessionLineUnderGroup", itemStreamingSessions, 0);

            List<Map<String, String>> garphidAndName =
                    assembleGarphidAndName(IngestSessionPie, IngestSessionLine, StreamingSessionPie, StreamingSessionLine, StreamingBindWidthPie, StreamingBindWidthLine);

            createDashboard(fms5000GroupIds, garphidAndName, fms5000.get(i) + "_Summary", true, i);*/
        }
    }

    private void deleteGraph() {
        List params = new ArrayList();

        params.addAll(getGraph(Arrays.asList(new String[]{"IngestSessionPieUnderGroup", "IngestSessionLineUnderGroup",
                "StreamingSessionPieUnderGroup", "StreamingSessionLineUnderGroup"})));
        if (params.size() == 0) return;
        String s = HttpUtil.sendPost(getRequestMap("graph.delete", params), ConfigUtil.ZABBIX_URL);
        System.out.println(s);

    }

    private List<String> getGraph(List<String> graphName) {
        List<String> graphids = new ArrayList<>();
        for (String s : graphName) {
            Map<String, Object> params = new HashMap<>();
            Map<String, Object> search = new HashMap<>();
            params.put("output", "extend");
            search.put("name", s);
            search.put("hostids", "127.0.0.1");
            params.put("search", search);
            String temp = HttpUtil.sendPost(getRequestMap("graph.get", params), ConfigUtil.ZABBIX_URL);
            JSONArray result = JSON.parseObject(temp).getJSONArray("result");
            for (Object o : result) {
                JSONObject jsonObject = (JSONObject) o;
                graphids.add(jsonObject.getString("graphid"));
            }
        }
        return graphids;
    }

    public static void main(String[] args) {
        new ZabbixGroupHostDashboard().deleteGraph();


    }

    public String getItem(String hosiIds, String itemKey, String returnField) {

        Map<String, Object> params = new HashMap<>();
        params.put("output", "extend");
        Map<String, Object> search = new HashMap<>();
        search.put("name", itemKey);
        params.put("search", search);
        params.put("hostids", hosiIds);

        String s = HttpUtil.sendPost(getRequestMap("item.get", params), ConfigUtil.ZABBIX_URL);
        Map map = parseJson(s);
        return (String) map.get(returnField);
    }

}
