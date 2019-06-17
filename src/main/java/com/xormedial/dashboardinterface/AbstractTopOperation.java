package com.xormedial.dashboardinterface;

import java.util.List;
import java.util.Map;

public interface AbstractTopOperation {

    String createItem(String name, String hostId, String key);

    String createGraph(String name, List<String> itemIdlist, int graphtype);

    List<String> creatHost(Integer hostNum, String ip, String hostBaseName, String groupId);

    String getHost();

    Map<String, List<String>> getHostGroupName();

    String getHostGroupId(String hostname);

    void createDashboard(List<String> groupIds, List<Map<String, String>> garphidAndName, String dashboardName);

    String getDashboard(String dashboardName);

    void deleteDashboard(String dashboardName);


}