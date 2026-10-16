package com.orvid.tor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Tor {

    public enum State { OFF, STARTING, READY, FAILED }

    public interface Listener {
        void onTorChanged();
    }

    private static final Pattern PROGRESS = Pattern.compile("PROGRESS=(\\d+)");
    private static final Pattern SUMMARY = Pattern.compile("SUMMARY=\"([^\"]*)\"");
    private static final Pattern LISTENER = Pattern.compile("127\\.0\\.0\\.1:(\\d+)");

    private static final Tor INSTANCE = new Tor(12, null);
    private static final Tor DACH = new Tor(2, "{de},{at},{ch}");

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Object lock = new Object();
    private volatile State state = State.OFF;
    private volatile int progress;
    private volatile String summary = "";
    private volatile int[] ports = new int[0];
    private Process process;
    private Process dying;
    private File dir;
    private String binary;
    private final int circuitPorts;
    private final String exitNodes;
    private Runnable setup;
    private volatile Bridges.Mode mode = Bridges.Mode.DIRECT;
    private volatile Bridges.Mode preferred = Bridges.Mode.DIRECT;
    private static final long STALL_MS = 30000;

    public Bridges.Mode mode() {
        return mode;
    }

    public void prefer(Bridges.Mode m) {
        preferred = m == null ? Bridges.Mode.DIRECT : m;
    }
    private int owner;

    private Tor(int circuitPorts, String exitNodes) {
        this.circuitPorts = circuitPorts;
        this.exitNodes = exitNodes;
    }

    public static Tor get() {
        return INSTANCE;
    }

    public static Tor dach() {
        return DACH;
    }

    public void prepare(File workDir, String torBinary, int ownerPid, Runnable beforeStart) {
        synchronized (lock) {
            dir = workDir;
            binary = torBinary;
            owner = ownerPid;
            setup = beforeStart;
        }
    }

    public void ensureStarted() {
        File d;
        Runnable before;
        synchronized (lock) {
            if (state == State.STARTING || state == State.READY || dir == null) return;
            d = dir;
            before = setup;
            if (this != INSTANCE) preferred = INSTANCE.mode;
        }
        if (before != null) before.run();
        start(d, binary, owner);
    }

    public State state() {
        return state;
    }

    public int progress() {
        return progress;
    }

    public String summary() {
        return summary;
    }

    public int[] ports() {
        return ports;
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void changed() {
        for (Listener l : listeners) l.onTorChanged();
    }

    public void start(File workDir, String torBinary, int ownerPid) {
        synchronized (lock) {
            if (state == State.STARTING || state == State.READY) return;
            dir = workDir;
            binary = torBinary;
            state = State.STARTING;
            progress = 0;
            summary = "";
            ports = new int[0];
        }
        changed();
        Thread t = new Thread(() -> {
            List<Bridges.Mode> plan = Bridges.order(preferred);
            int attempts = Math.max(3, plan.size() * 2);
            for (int attempt = 0; attempt < attempts; attempt++) {
                mode = plan.get(attempt % plan.size());
                if (run(ownerPid)) return;
                synchronized (lock) {
                    if (state != State.FAILED || attempt == attempts - 1) return;
                    state = State.STARTING;
                    progress = 0;
                }
                changed();
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "orvid-tor");
        t.setDaemon(true);
        t.start();
    }

    public void stop() {
        Process p;
        synchronized (lock) {
            p = process;
            process = null;
            state = State.OFF;
            progress = 0;
            ports = new int[0];
            lock.notifyAll();
        }
        if (p != null) {
            p.destroy();
            synchronized (lock) {
                dying = p;
            }
        }
        changed();
    }

    public boolean awaitReady(long timeoutMs) throws InterruptedException {
        long end = System.currentTimeMillis() + timeoutMs;
        synchronized (lock) {
            while (state == State.STARTING) {
                long left = end - System.currentTimeMillis();
                if (left <= 0) break;
                lock.wait(left);
            }
            return state == State.READY;
        }
    }

    private boolean run(int ownerPid) {
        try {
            Process old;
            synchronized (lock) {
                old = dying;
                dying = null;
            }
            if (old != null) old.waitFor();
            File data = new File(dir, "data");
            data.mkdirs();
            File controlFile = new File(dir, "control.txt");
            controlFile.delete();
            File torrc = new File(dir, "torrc");
            StringBuilder rc = new StringBuilder()
                    .append("DataDirectory ").append(data.getAbsolutePath()).append('\n')
                    .append("SocksPort 0\n")
                    .append("ControlPort auto\n")
                    .append("ControlPortWriteToFile ").append(controlFile.getAbsolutePath()).append('\n')
                    .append("CookieAuthentication 1\n")
                    .append("CookieAuthFile ").append(new File(dir, "cookie").getAbsolutePath()).append('\n')
                    .append("__OwningControllerProcess ").append(ownerPid).append('\n')
                    .append("ClientOnly 1\n")
                    .append("AvoidDiskWrites 1\n")
                    .append("MaxCircuitDirtiness 1800\n")
                    .append("Log notice stdout\n");
            if (exitNodes != null) {
                rc.append("GeoIPFile ").append(new File(dir, "geoip").getAbsolutePath()).append('\n')
                        .append("GeoIPv6File ").append(new File(dir, "geoip6").getAbsolutePath()).append('\n')
                        .append("ExitNodes ").append(exitNodes).append('\n')
                        .append("StrictNodes 1\n");
            }
            rc.append(Bridges.torrc(mode));
            for (int i = 0; i < circuitPorts; i++) rc.append("HTTPTunnelPort auto\n");
            try (FileOutputStream out = new FileOutputStream(torrc)) {
                out.write(rc.toString().getBytes(StandardCharsets.UTF_8));
            }

            Process p = new ProcessBuilder(binary, "-f", torrc.getAbsolutePath())
                    .redirectErrorStream(true).start();
            synchronized (lock) {
                if (state != State.STARTING) {
                    p.destroy();
                    return true;
                }
                process = p;
            }
            drain(p.getInputStream());

            long deadline = System.currentTimeMillis() + mode.budgetMs;
            long movedAt = System.currentTimeMillis();
            while (!controlFile.isFile() || controlFile.length() == 0) {
                if (!alive(p) || System.currentTimeMillis() > deadline) throw new IOException("tor did not start");
                Thread.sleep(100);
            }
            while (true) {
                if (!alive(p)) throw new IOException("tor exited");
                long now = System.currentTimeMillis();
                if (now > deadline) throw new IOException("tor bootstrap timeout");
                if (now - movedAt > (mode == Bridges.Mode.DIRECT ? STALL_MS : STALL_MS * 2)) throw new IOException("tor bootstrap stalled at " + progress + "%");
                String phase = control("GETINFO status/bootstrap-phase");
                Matcher m = PROGRESS.matcher(phase);
                Matcher s = SUMMARY.matcher(phase);
                int pct = m.find() ? Integer.parseInt(m.group(1)) : progress;
                String sum = s.find() ? s.group(1) : summary;
                if (pct != progress || !sum.equals(summary)) {
                    movedAt = System.currentTimeMillis();
                    progress = pct;
                    summary = sum;
                    changed();
                }
                if (pct >= 100) break;
                Thread.sleep(500);
            }
            List<Integer> found = new ArrayList<>();
            Matcher l = LISTENER.matcher(control("GETINFO net/listeners/httptunnel"));
            while (l.find()) found.add(Integer.parseInt(l.group(1)));
            if (found.isEmpty()) throw new IOException("no tunnel ports");
            int[] arr = new int[found.size()];
            for (int i = 0; i < arr.length; i++) arr[i] = found.get(i);
            synchronized (lock) {
                if (state != State.STARTING) return true;
                ports = arr;
                state = State.READY;
                lock.notifyAll();
            }
            changed();
            p.waitFor();
            synchronized (lock) {
                if (process != p) return true;
                process = null;
                state = State.FAILED;
                summary = "tor exited";
                ports = new int[0];
                lock.notifyAll();
            }
            changed();
            return false;
        } catch (Exception e) {
            Process p;
            synchronized (lock) {
                if (state != State.STARTING) return true;
                p = process;
                process = null;
                state = State.FAILED;
                summary = e.getMessage() == null ? e.toString() : e.getMessage();
                lock.notifyAll();
            }
            if (p != null) p.destroy();
            changed();
            return false;
        }
    }

    private static boolean alive(Process p) {
        try {
            p.exitValue();
            return false;
        } catch (IllegalThreadStateException e) {
            return true;
        }
    }

    private static void drain(InputStream in) {
        Thread t = new Thread(() -> {
            byte[] buf = new byte[4096];
            try {
                while (in.read(buf) > 0) {
                }
            } catch (IOException ignored) {
            }
        }, "orvid-tor-log");
        t.setDaemon(true);
        t.start();
    }

    private String control(String command) throws IOException {
        String line = new String(Files.readAllBytes(new File(dir, "control.txt").toPath()),
                StandardCharsets.US_ASCII).trim();
        int port = Integer.parseInt(line.substring(line.lastIndexOf(':') + 1));
        byte[] cookie = Files.readAllBytes(new File(dir, "cookie").toPath());
        StringBuilder hex = new StringBuilder();
        for (byte b : cookie) hex.append(String.format("%02x", b));
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", port), 3000);
            s.setSoTimeout(5000);
            OutputStream out = s.getOutputStream();
            out.write(("AUTHENTICATE " + hex + "\r\n" + command + "\r\nQUIT\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            out.flush();
            BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder reply = new StringBuilder();
            for (String l = r.readLine(); l != null; l = r.readLine()) {
                if (l.startsWith("515") || l.startsWith("514")) throw new IOException("control auth failed");
                reply.append(l).append('\n');
            }
            return reply.toString();
        }
    }
}
