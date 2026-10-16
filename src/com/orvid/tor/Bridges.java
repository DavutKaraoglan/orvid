package com.orvid.tor;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import IPtProxy.Controller;
import IPtProxy.OnTransportEvents;

public final class Bridges {

    public enum Mode {
        DIRECT(null, 180000),
        OBFS4("obfs4", 90000),
        SNOWFLAKE("snowflake", 120000),
        MEEK("meek_lite", 150000);

        final String transport;
        final long budgetMs;

        Mode(String transport, long budgetMs) {
            this.transport = transport;
            this.budgetMs = budgetMs;
        }
    }

    private static final Object LOCK = new Object();
    private static String stateDir;
    private static JsonObject lines = new JsonObject();
    private static Controller controller;

    private Bridges() {
    }

    public static void configure(String dir, String json) {
        synchronized (LOCK) {
            stateDir = dir;
            try {
                lines = JsonParser.object().from(json);
            } catch (Exception e) {
                lines = new JsonObject();
            }
        }
    }

    private static volatile boolean enabled;

    public static void setEnabled(boolean on) {
        enabled = on;
    }

    static List<Mode> order(Mode last) {
        List<Mode> out = new ArrayList<>();
        if (enabled) {
            if (last != Mode.DIRECT && available(last)) out.add(last);
            for (Mode m : Mode.values()) if (m != Mode.DIRECT && !out.contains(m) && available(m)) out.add(m);
        }
        if (out.isEmpty()) out.add(Mode.DIRECT);
        return out;
    }

    static boolean available(Mode m) {
        if (m == Mode.DIRECT) return true;
        synchronized (LOCK) {
            return stateDir != null && !bridgeLines(m).isEmpty();
        }
    }

    static String torrc(Mode m) throws Exception {
        if (m == Mode.DIRECT) return "";
        List<String> bridges;
        Controller c;
        long port;
        try {
            synchronized (LOCK) {
                bridges = bridgeLines(m);
                c = controller();
                if (m == Mode.SNOWFLAKE && !bridges.isEmpty()) snowflake(c, bridges.get(0));
            }
            if (c.port(m.transport) <= 0) c.start(m.transport, "");
            port = c.port(m.transport);
        } catch (LinkageError e) {
            throw new java.io.IOException(m.transport + " unavailable", e);
        }
        if (port <= 0) throw new IllegalStateException(m.transport + " did not start");
        StringBuilder rc = new StringBuilder("UseBridges 1\n")
                .append("ClientTransportPlugin ").append(m.transport).append(" socks5 127.0.0.1:").append(port).append('\n');
        for (String line : bridges) rc.append("Bridge ").append(line).append('\n');
        return rc.toString();
    }

    private static List<String> bridgeLines(Mode m) {
        String key = m == Mode.MEEK ? "meek" : m.transport;
        JsonArray a = lines.getArray(key);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < a.size(); i++) {
            String line = a.getString(i, "");
            if (line.startsWith(m.transport + " ")) out.add(line);
        }
        return out;
    }

    private static Controller controller() {
        if (controller == null) {
            new java.io.File(stateDir).mkdirs();
            controller = new Controller(stateDir, false, false, "ERROR", new OnTransportEvents() {
                @Override
                public void connected(String name) {
                }

                @Override
                public void error(String name, Exception error) {
                }

                @Override
                public void stopped(String name, Exception error) {
                }
            });
        }
        return controller;
    }

    private static void snowflake(Controller c, String line) {
        for (String part : Arrays.asList(line.split(" "))) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            String key = part.substring(0, eq);
            String value = part.substring(eq + 1);
            if (key.equals("url")) c.setSnowflakeBrokerUrl(value);
            else if (key.equals("fronts")) c.setSnowflakeFrontDomains(value);
            else if (key.equals("ice")) c.setSnowflakeIceServers(value);
        }
    }
}
