package com.xormedial.context;

import com.xormedial.dashboardinterface.Dashboard;

public class StrategyContext {

    private Dashboard dashboard;

    public StrategyContext(Dashboard dashboard){
        this.dashboard = dashboard;
    }

    public void excute(){
        dashboard.createFinalDashboard();
    }

}
