package com.xormedial.dashboardinterface;

import java.util.List;
import java.util.Map;

public interface AbstractTopOperation {

    String createItem(String name, String hostId, String key);

    String createGraph(String name, List<String> itemIdlist, int graphtype);

    List<String> creatHost(List<String> hostNum, String ip, String groupId);

    List<String> getHost(String groupId);

    Map<String, List<String>> getHostGroupName();

    String getHostGroupId(String hostname);

    void createDashboard(List<String> groupIds, List<Map<String, String>> garphidAndName, String dashboardName, boolean flag, int index);

    String getDashboard(String dashboardName);

    void deleteDashboard(String dashboardName);


}
