package com.orvid.net;

import com.orvid.tor.Tor;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.zip.GZIPInputStream;

public final class Fetch extends Downloader {
    public static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0";
    private static final int TIMEOUT_MS = 20000;

    private static final long TOR_WAIT_MS = 120000;
    private static final Fetch INSTANCE = new Fetch();
    private static final ThreadLocal<String> CIRCUIT = new ThreadLocal<>();
    private static final ThreadLocal<Scope> SCOPE = new ThreadLocal<>();

    public static final class Cancelled extends IOException {
        Cancelled() {
            super("cancelled");
        }
    }

    public static final class Scope {
        private volatile boolean cancelled;
        private final java.util.Set<HttpURLConnection> live =
                java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

        public boolean cancelled() {
            return cancelled;
        }

        public void cancel() {
            java.util.List<HttpURLConnection> open;
            synchronized (live) {
                cancelled = true;
                open = new java.util.ArrayList<>(live);
                live.clear();
            }
            for (HttpURLConnection c : open) {
                try {
                    c.disconnect();
                } catch (RuntimeException ignored) {
                }
            }
        }

        void add(HttpURLConnection c) throws IOException {
            synchronized (live) {
                if (cancelled) throw new Cancelled();
                live.add(c);
            }
        }
    }

    public static Scope scope() {
        return SCOPE.get();
    }

    public static <T> T within(Scope scope, Callable<T> work) throws Exception {
        Scope previous = SCOPE.get();
        SCOPE.set(scope);
        try {
            if (scope != null && scope.cancelled()) throw new Cancelled();
            return work.call();
        } finally {
            if (previous == null) SCOPE.remove();
            else SCOPE.set(previous);
        }
    }
    private static final Map<String, Integer> SLOTS = new LinkedHashMap<String, Integer>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
            return size() > 256;
        }
    };
    private static int nextSlot;
    private static volatile boolean torMode;

    private Fetch() {
    }

    public static Fetch get() {
        return INSTANCE;
    }

    public static void setTorMode(boolean on) {
        torMode = on;
    }

    public static boolean torMode() {
        return torMode;
    }

    public static String newCircuit(String prefix) {
        return prefix + ":" + Long.toHexString(Double.doubleToLongBits(Math.random()));
    }

    public static <T> T on(String circuit, Callable<T> work) throws Exception {
        String previous = CIRCUIT.get();
        CIRCUIT.set(circuit);
        try {
            return work.call();
        } finally {
            if (previous == null) CIRCUIT.remove();
            else CIRCUIT.set(previous);
        }
    }

    public interface Attempt<T> {
        T run(String circuit) throws Exception;
    }

    public static <T> T retrying(String circuit, Attempt<T> work) throws Exception {
        int tries = torMode ? 4 : 1;
        for (int i = 1; ; i++) {
            try {
                return on(circuit, () -> work.run(circuit));
            } catch (Exception e) {
                Scope scope = SCOPE.get();
                if (scope != null && scope.cancelled()) throw new Cancelled();
                if (i >= tries || !blocked(e)) throw e;
                Diag.mark("blocked on try " + i + ", new circuit: " + e.getClass().getSimpleName());
                rotate(circuit);
            }
        }
    }

    public static boolean blocked(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String name = t.getClass().getSimpleName();
            String msg = String.valueOf(t.getMessage());
            if (name.equals("ReCaptchaException") || name.equals("SignInConfirmNotBotException")) return true;
            if (msg.contains("Got HTML document") || msg.contains("not a bot") || msg.contains("Captcha needed")) return true;
            if (t instanceof javax.net.ssl.SSLException) return true;
            if (t instanceof java.net.SocketTimeoutException) return true;
        }
        return false;
    }

    public static void rotate(String circuit) {
        synchronized (SLOTS) {
            SLOTS.put(circuit, nextSlot++);
        }
    }

    public static final class TorUnavailable extends IOException {
        TorUnavailable() {
            super("Tor is not connected");
        }
    }

    private static int slot(String circuit) {
        synchronized (SLOTS) {
            Integer s = SLOTS.get(circuit == null ? "default" : circuit);
            if (s == null) {
                s = nextSlot++;
                SLOTS.put(circuit == null ? "default" : circuit, s);
            }
            return s;
        }
    }

    public static final String DACH = "dach:";

    public static String dachCircuit() {
        return newCircuit("dach");
    }

    public static int torPortFor(String circuit) throws IOException {
        Tor tor = torFor(circuit);
        try {
            if (!tor.awaitReady(TOR_WAIT_MS)) throw new TorUnavailable();
        } catch (InterruptedException e) {
            throw new IOException("interrupted");
        }
        int[] ports = tor.ports();
        if (ports.length == 0) throw new TorUnavailable();
        return ports[Math.floorMod(slot(circuit), ports.length)];
    }

    private static Tor torFor(String circuit) {
        if (circuit == null || !circuit.startsWith(DACH)) return Tor.get();
        Tor.dach().ensureStarted();
        return Tor.dach();
    }

    private static Proxy route() throws IOException {
        if (!torMode) return Proxy.NO_PROXY;
        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", torPortFor(CIRCUIT.get())));
    }

    @Override
    public Response execute(Request request) throws IOException, ReCaptchaException {
        long began = System.currentTimeMillis();
        String url = request.url();
        int q = url.indexOf('?');
        String shortUrl = (q > 0 ? url.substring(0, q) : url).replaceFirst("^https://", "");
        if (shortUrl.length() > 70) shortUrl = shortUrl.substring(0, 70);
        Diag.mark("net > " + request.httpMethod() + " " + shortUrl + " [" + CIRCUIT.get() + "]");
        try {
            Response r = executeInner(request);
            Diag.mark("net < " + r.responseCode() + " " + r.responseBody().length() / 1024 + "KB "
                    + (System.currentTimeMillis() - began) + "ms " + shortUrl);
            return r;
        } catch (IOException | RuntimeException e) {
            Diag.mark("net ! " + e.getClass().getSimpleName() + " " + e.getMessage() + " "
                    + (System.currentTimeMillis() - began) + "ms " + shortUrl);
            throw e;
        }
    }

    private Response executeInner(Request request) throws IOException, ReCaptchaException {
        HttpURLConnection c = open(request.url());
        c.setRequestMethod(request.httpMethod());
        boolean userAgentSet = false;
        for (Map.Entry<String, List<String>> h : request.headers().entrySet()) {
            String name = h.getKey();
            if (name.equalsIgnoreCase("User-Agent")) userAgentSet = true;
            c.setRequestProperty(name, String.join(", ", h.getValue()));
        }
        if (!userAgentSet) c.setRequestProperty("User-Agent", USER_AGENT);
        c.setRequestProperty("Accept-Encoding", "gzip");

        byte[] body = request.dataToSend();
        if (body != null) {
            c.setDoOutput(true);
            c.setFixedLengthStreamingMode(body.length);
            try (OutputStream out = c.getOutputStream()) {
                out.write(body);
            }
        }

        int code = c.getResponseCode();
        if (code == 429) {
            readBody(c);
            throw new ReCaptchaException("rate limited", request.url());
        }
        String text = "HEAD".equals(request.httpMethod()) ? "" : readBody(c);
        return new Response(code, c.getResponseMessage(), c.getHeaderFields(), text,
                c.getURL().toString());
    }

    public static HttpURLConnection open(String url) throws IOException {
        Scope scope = SCOPE.get();
        if (scope != null && scope.cancelled()) throw new Cancelled();
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection(route());
        if (scope != null) scope.add(c);
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setUseCaches(false);
        c.setInstanceFollowRedirects(true);
        return c;
    }

    public static byte[] bytes(String url) throws IOException {
        HttpURLConnection c = open(url);
        c.setRequestProperty("User-Agent", USER_AGENT);
        int code = c.getResponseCode();
        if (code >= 400) {
            readBody(c);
            throw new IOException("HTTP " + code);
        }
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[16384];
            int n;
            while ((n = in.read(chunk)) > 0) buf.write(chunk, 0, n);
            return buf.toByteArray();
        }
    }

    private static String readBody(HttpURLConnection c) throws IOException {
        InputStream in;
        try {
            in = c.getInputStream();
        } catch (IOException e) {
            in = c.getErrorStream();
            if (in == null) return "";
        }
        if ("gzip".equalsIgnoreCase(c.getContentEncoding())) in = new GZIPInputStream(in);
        try (InputStream s = in) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[16384];
            int n;
            while ((n = s.read(chunk)) > 0) buf.write(chunk, 0, n);
            return new String(buf.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
