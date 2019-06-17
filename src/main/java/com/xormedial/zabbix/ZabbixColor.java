package com.xormedial.zabbix;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ZabbixColor {

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

