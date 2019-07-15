package com.xormedial;

import com.xormedial.context.StrategyContext;
import com.xormedial.dashboardinterface.Dashboard;
import com.xormedial.zabbix.ZabbixDashboard;

public class CreateDashboardApplication {

	public static void main(String[] args) {
        Dashboard dashboard = new ZabbixDashboard();
        StrategyContext strategyContext = new StrategyContext(dashboard);
        strategyContext.excute();

	}

}
