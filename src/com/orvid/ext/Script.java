package com.orvid.ext;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.orvid.net.Fetch;

import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Map;

final class Script {

    private static final long CPU_LIMIT_MS = 5000;
    private static final int MAX_BODY = 4 * 1024 * 1024;
    private static final String PRELUDE =
            "var http = { json: function (u, h) { return JSON.parse(http.get(u, h)); }, get: __get, post: __post };\n"
            + "var util = { duration: __duration, number: __number, unescape: __unescape };\n"
            + "function __call(name, a, b) { return JSON.stringify(this[name](a, b)); }\n";

    private static final ContextFactory FACTORY = new ContextFactory() {
        @Override
        protected boolean hasFeature(Context cx, int feature) {
            if (feature == Context.FEATURE_ENABLE_JAVA_MAP_ACCESS) return false;
            return super.hasFeature(cx, feature);
        }

        @Override
        protected void observeInstructionCount(Context cx, int instructionCount) {
            Object started = cx.getThreadLocal("started");
            if (started instanceof Long && System.currentTimeMillis() - (Long) started > CPU_LIMIT_MS) {
                throw new Error("script ran too long");
            }
        }
    };

    private final String name;
    private final String code;

    Script(String name, String code) {
        this.name = name;
        this.code = code;
    }

    JsonObject call(String circuit, String function, String a, String b) throws IOException {
        Context cx = FACTORY.enterContext();
        try {
            cx.setInterpretedMode(true);
            cx.setClassShutter(className -> false);
            cx.setInstructionObserverThreshold(10000);
            cx.setMaximumInterpreterStackDepth(200);
            cx.putThreadLocal("started", System.currentTimeMillis());
            ScriptableObject scope = cx.initSafeStandardObjects();
            host(scope, circuit);
            cx.evaluateString(scope, PRELUDE, "prelude", 1, null);
            cx.evaluateString(scope, code, name, 1, null);
            Object fn = scope.get("__call", scope);
            if (!(fn instanceof Function)) throw new IOException(name + ": no entry");
            Object out = ((Function) fn).call(cx, scope, scope, new Object[] {function, a, b});
            String json = Context.toString(out);
            return JsonParser.object().from(json);
        } catch (IOException e) {
            throw e;
        } catch (Throwable e) {
            Throwable cause = e;
            while (cause.getCause() != null) cause = cause.getCause();
            if (cause instanceof IOException) throw (IOException) cause;
            throw new IOException(name + ": " + e.getMessage(), e);
        } finally {
            Context.exit();
        }
    }

    private static void host(ScriptableObject scope, String circuit) {
        define(scope, "__get", args -> fetch(circuit, str(args, 0), null, null, headers(args, 1)));
        define(scope, "__post", args -> fetch(circuit, str(args, 0), str(args, 1), str(args, 2), headers(args, 3)));
        define(scope, "__duration", args -> (double) com.orvid.adult.Web.duration(str(args, 0)));
        define(scope, "__number", args -> (double) com.orvid.adult.Web.number(str(args, 0)));
        define(scope, "__unescape", args -> com.orvid.adult.Web.unescape(str(args, 0)));
    }

    private interface Host {
        Object run(Object[] args) throws Exception;
    }

    private static void define(ScriptableObject scope, String name, Host host) {
        scope.defineProperty(name, new BaseFunction() {
            @Override
            public Object call(Context cx, Scriptable s, Scriptable thisObj, Object[] args) {
                try {
                    return host.run(args);
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new HostError(e);
                }
            }
        }, ScriptableObject.READONLY | ScriptableObject.PERMANENT);
    }

    private static final class HostError extends RuntimeException {
        HostError(Exception cause) {
            super(cause.getMessage(), cause);
        }
    }

    private static String str(Object[] args, int i) {
        if (i >= args.length || args[i] == null || args[i] instanceof org.mozilla.javascript.Undefined) return null;
        return Context.toString(args[i]);
    }

    private static NativeObject headers(Object[] args, int i) {
        return i < args.length && args[i] instanceof NativeObject ? (NativeObject) args[i] : null;
    }

    private static String fetch(String circuit, String url, String body, String type, NativeObject headers) throws Exception {
        if (url == null || !url.startsWith("https://") && !url.startsWith("http://")) throw new IOException("bad url");
        return Fetch.on(circuit, () -> {
            HttpURLConnection c = Fetch.open(url);
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setRequestProperty("User-Agent", Fetch.USER_AGENT);
            c.setRequestProperty("Accept-Language", "en-US,en;q=0.8");
            if (headers != null) {
                for (Map.Entry<Object, Object> e : headers.entrySet()) {
                    c.setRequestProperty(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
                }
            }
            if (body != null) {
                byte[] raw = body.getBytes(StandardCharsets.UTF_8);
                c.setRequestMethod("POST");
                c.setRequestProperty("Content-Type", type == null ? "application/x-www-form-urlencoded" : type);
                c.setDoOutput(true);
                c.setFixedLengthStreamingMode(raw.length);
                try (OutputStream out = c.getOutputStream()) {
                    out.write(raw);
                }
            }
            int code = c.getResponseCode();
            if (code == 403 || code == 429) throw new IOException("Captcha needed (HTTP " + code + ")");
            if (code >= 400) throw new IOException("HTTP " + code);
            InputStream in = c.getInputStream();
            if ("gzip".equalsIgnoreCase(c.getContentEncoding())) in = new java.util.zip.GZIPInputStream(in);
            try (InputStream body2 = in) {
                byte[] got = body2.readNBytes(MAX_BODY);
                return new String(got, StandardCharsets.UTF_8);
            }
        });
    }

    static JsonArray array(JsonObject o, String key) {
        JsonArray a = o.getArray(key);
        return a == null ? new JsonArray() : a;
    }
}
