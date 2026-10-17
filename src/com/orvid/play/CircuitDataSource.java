package com.orvid.play;

import android.net.Uri;

import androidx.media3.common.C;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.HttpDataSource;

import com.orvid.net.Diag;
import com.orvid.net.Fetch;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class CircuitDataSource extends BaseDataSource {

    private static final long CHUNK = 10L * 1024 * 1024;
    private static final long HEAD_PART = 512L * 1024;
    private static final int PARTS = 3;
    private static final java.util.concurrent.ExecutorService PARALLEL =
            java.util.concurrent.Executors.newCachedThreadPool();

    static boolean parallelHost(String url) {
        String host = Uri.parse(url).getHost();
        if (host == null) return false;
        return host.endsWith(".cloudfront.net") || host.endsWith(".ttvnw.net") || host.endsWith(".twitchcdn.net");
    }

    public static final class Factory implements DataSource.Factory {
        private final String circuit;
        private final String userAgent;
        private final String referer;

        public Factory(String circuit, String userAgent) {
            this(circuit, userAgent, null);
        }

        public Factory(String circuit, String userAgent, String referer) {
            this.circuit = circuit;
            this.userAgent = userAgent;
            this.referer = referer;
        }

        @Override
        public DataSource createDataSource() {
            CircuitDataSource d = new CircuitDataSource(circuit, userAgent);
            d.referer = referer;
            return d;
        }
    }

    private final String circuit;
    private final String userAgent;
    private String referer;
    private DataSpec spec;
    private HttpURLConnection connection;
    private InputStream stream;
    private long position;
    private long end;
    private long chunkEnd;
    private Map<String, List<String>> headers = Collections.emptyMap();
    private boolean opened;
    private long chunkSize = CHUNK;
    private long partsFrom = -1;
    private long[] partStart;
    private java.util.List<java.util.concurrent.Future<byte[]>> parts;

    private CircuitDataSource(String circuit, String userAgent) {
        super(true);
        this.circuit = circuit;
        this.userAgent = userAgent;
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        spec = dataSpec;
        transferInitializing(dataSpec);
        position = dataSpec.position;
        end = dataSpec.length == C.LENGTH_UNSET ? -1 : dataSpec.position + dataSpec.length;
        boolean parallel = dataSpec.position == 0 && dataSpec.length == C.LENGTH_UNSET
                && parallelHost(dataSpec.uri.toString());
        chunkSize = parallel ? HEAD_PART : CHUNK;
        partsFrom = -1;
        parts = null;
        long total = connect();
        if (parallel && total > HEAD_PART * 2 && chunkEnd == HEAD_PART) startParts(total);
        opened = true;
        transferStarted(dataSpec);
        if (dataSpec.length != C.LENGTH_UNSET) return dataSpec.length;
        if (total > 0) {
            end = total;
            return total - position;
        }
        return C.LENGTH_UNSET;
    }

    private String effective;
    private Uri effectiveFor;

    private int request(String url, long chunkLast) throws IOException {
        try {
            connection = Fetch.on(circuit, () -> Fetch.open(url));
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }
        connection.setRequestProperty("User-Agent", userAgent);
        connection.setRequestProperty("Range", "bytes=" + position + "-" + chunkLast);
        connection.setRequestProperty("Accept-Encoding", "identity");
        if (referer != null) {
            connection.setRequestProperty("Referer", referer);
            connection.setRequestProperty("Origin", referer.replaceAll("/$", ""));
        }
        for (Map.Entry<String, String> h : spec.httpRequestHeaders.entrySet()) {
            connection.setRequestProperty(h.getKey(), h.getValue());
        }
        if (com.orvid.dm.Dailymotion.needsUnblock(url)) {
            for (Map.Entry<String, String> h : com.orvid.dm.Dailymotion.unblockHeaders().entrySet()) {
                connection.setRequestProperty(h.getKey(), h.getValue());
            }
        }
        return connection.getResponseCode();
    }

    private long connect() throws IOException {
        closeConnection();
        long last = end > 0 ? end - 1 : -1;
        long chunkLast = position + chunkSize - 1;
        if (last >= 0 && last < chunkLast) chunkLast = last;
        chunkEnd = chunkLast + 1;
        boolean reused = effective != null && effectiveFor == spec.uri;
        String url = reused ? effective : spec.uri.toString();
        long began = System.currentTimeMillis();
        int code = 0;
        for (int attempt = 0; attempt < 2; attempt++) {
            code = request(url, chunkLast);
            for (int hop = 0; hop < 5 && code >= 300 && code < 400; hop++) {
                String location = connection.getHeaderField("Location");
                if (location == null) break;
                String next = java.net.URI.create(url).resolve(location).toString().replaceFirst("^http://", "https://");
                closeConnection();
                url = next;
                code = request(url, chunkLast);
            }
            if (code == 200 || code == 206 || !reused) break;
            closeConnection();
            reused = false;
            effective = null;
            url = spec.uri.toString();
        }
        if (code == 200 || code == 206) {
            effective = url;
            effectiveFor = spec.uri;
        }
        headers = connection.getHeaderFields();
        if (code != 206 && code != 200) {
            Diag.mark("media HTTP " + code + " " + kind(url));
            closeConnection();
            throw new HttpDataSource.InvalidResponseCodeException(code, null, null, headers, spec, new byte[0]);
        }
        long total = -1;
        String range = connection.getHeaderField("Content-Range");
        if (range != null && range.contains("/")) {
            String tail = range.substring(range.lastIndexOf('/') + 1).trim();
            if (!tail.equals("*")) total = Long.parseLong(tail);
        }
        if (code == 200) {
            chunkEnd = Long.MAX_VALUE;
            long len = connection.getContentLengthLong();
            if (len > 0 && position == 0) total = len;
            if (position > 0) {
                InputStream in = connection.getInputStream();
                long skip = position;
                while (skip > 0) {
                    long n = in.skip(skip);
                    if (n <= 0) throw new IOException("cannot skip");
                    skip -= n;
                }
            }
        }
        stream = connection.getInputStream();
        Diag.mark("media " + kind(url) + " @" + position / 1024 + "KB " + code + " "
                + (System.currentTimeMillis() - began) + "ms");
        return total;
    }

    private static String kind(String url) {
        int i = url.indexOf("itag=");
        if (i < 0) i = url.indexOf("/itag/");
        if (i < 0) return url.contains("m3u8") || url.contains("manifest") ? "playlist" : "file";
        int s = url.charAt(i) == '/' ? i + 6 : i + 5;
        int e = s;
        while (e < url.length() && Character.isDigit(url.charAt(e))) e++;
        return "itag" + url.substring(s, e);
    }

    private void startParts(long total) {
        String url = spec.uri.toString();
        partsFrom = HEAD_PART;
        long size = (total - HEAD_PART + PARTS - 1) / PARTS;
        partStart = new long[PARTS + 1];
        parts = new java.util.ArrayList<>();
        for (int i = 0; i < PARTS; i++) {
            long from = HEAD_PART + i * size;
            long to = Math.min(total, from + size);
            partStart[i] = from;
            partStart[i + 1] = to;
            final String c = i == 0 ? circuit : circuit + "#p" + i;
            parts.add(PARALLEL.submit(() -> fetchRange(c, url, from, to)));
        }
        Diag.mark("media parallel " + PARTS + " parts of " + (total - HEAD_PART) / 1024 + "KB");
    }

    private byte[] fetchRange(String c, String url, long from, long to) throws Exception {
        return Fetch.on(c, () -> {
            HttpURLConnection h = Fetch.open(url);
            h.setRequestProperty("User-Agent", userAgent);
            h.setRequestProperty("Accept-Encoding", "identity");
            h.setRequestProperty("Range", "bytes=" + from + "-" + (to - 1));
            int code = h.getResponseCode();
            if (code != 206) throw new IOException("part HTTP " + code);
            try (InputStream in = h.getInputStream()) {
                java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream((int) (to - from));
                byte[] chunk = new byte[65536];
                for (int n = in.read(chunk); n > 0; n = in.read(chunk)) buf.write(chunk, 0, n);
                if (buf.size() != to - from) throw new IOException("short part");
                return buf.toByteArray();
            }
        });
    }

    private byte[] part(int i) throws IOException {
        try {
            return parts.get(i).get(60, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            Diag.mark("media part " + i + " failed, retrying on main circuit");
            try {
                byte[] again = fetchRange(circuit, spec.uri.toString(), partStart[i], partStart[i + 1]);
                parts.set(i, java.util.concurrent.CompletableFuture.completedFuture(again));
                return again;
            } catch (IOException io) {
                throw io;
            } catch (Exception other) {
                throw new IOException(other);
            }
        }
    }

    private int readParts(byte[] buffer, int offset, int length) throws IOException {
        int i = 0;
        while (i < PARTS && position >= partStart[i + 1]) i++;
        if (i >= PARTS) return C.RESULT_END_OF_INPUT;
        byte[] data = part(i);
        int at = (int) (position - partStart[i]);
        int n = Math.min(length, data.length - at);
        System.arraycopy(data, at, buffer, offset, n);
        position += n;
        bytesTransferred(n);
        return n;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (length == 0) return 0;
        if (end > 0 && position >= end) return C.RESULT_END_OF_INPUT;
        if (parts != null && position >= partsFrom) return readParts(buffer, offset, length);
        if (position >= chunkEnd) connect();
        int n = stream.read(buffer, offset, length);
        if (n < 0) {
            if (chunkEnd != Long.MAX_VALUE && (end < 0 || position < end)) {
                connect();
                n = stream.read(buffer, offset, length);
                if (n < 0) return C.RESULT_END_OF_INPUT;
            } else {
                return C.RESULT_END_OF_INPUT;
            }
        }
        position += n;
        bytesTransferred(n);
        return n;
    }

    @Override
    public Uri getUri() {
        return spec == null ? null : spec.uri;
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
        return headers;
    }

    private void closeConnection() {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException ignored) {
            }
            stream = null;
        }
        connection = null;
    }

    @Override
    public void close() {
        closeConnection();
        if (parts != null) {
            for (java.util.concurrent.Future<byte[]> f : parts) f.cancel(true);
            parts = null;
        }
        if (opened) {
            opened = false;
            transferEnded();
        }
        spec = null;
    }
}
