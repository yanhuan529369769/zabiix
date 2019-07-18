package com.xormedial;

import com.xormedial.context.StrategyContext;
import com.xormedial.dashboardinterface.Dashboard;
import com.xormedial.zabbix.ZabbixGroupHostDashboard;

public class CreateDashboardApplication {

	public static void main(String[] args) {
        Dashboard dashboard = new ZabbixGroupHostDashboard();
        StrategyContext strategyContext = new StrategyContext(dashboard);
        strategyContext.excute();

	}

}
