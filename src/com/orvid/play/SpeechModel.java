package com.orvid.play;

import com.orvid.net.Diag;
import com.orvid.net.Fetch;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.security.MessageDigest;

public final class SpeechModel {

    public interface Progress {
        void update(long done, long total);
    }

    public interface Listener extends Progress {
        void finished(boolean ok);
    }

    private static final Object LOCK = new Object();
    private static final java.util.List<Listener> LISTENERS = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static Thread running;
    private static volatile long lastDone;

    public static void ensure(File filesDir, String circuit, Listener listener) {
        if (ready(filesDir)) {
            listener.finished(true);
            return;
        }
        synchronized (LOCK) {
            LISTENERS.add(listener);
            if (running != null) {
                listener.update(lastDone, SIZE);
                return;
            }
            lastDone = 0;
            running = new Thread(() -> {
                boolean ok;
                try {
                    download(filesDir, circuit, (done, total) -> {
                        lastDone = done;
                        for (Listener l : LISTENERS) l.update(done, total);
                    });
                    ok = true;
                } catch (Exception e) {
                    Diag.mark("speech model FAIL " + e);
                    ok = false;
                }
                java.util.List<Listener> waiting;
                synchronized (LOCK) {
                    running = null;
                    waiting = new java.util.ArrayList<>(LISTENERS);
                    LISTENERS.clear();
                }
                for (Listener l : waiting) l.finished(ok);
            }, "orvid-speech-model");
            running.start();
        }
    }

    public static void detach(Listener listener) {
        LISTENERS.remove(listener);
    }

    public static final String NAME = "ggml-base-q5_1.bin";
    public static final long SIZE = 59707625L;
    private static final String SHA256 = "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898";
    private static final String URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/" + NAME;

    private SpeechModel() {
    }

    public static File file(File filesDir) {
        return new File(new File(filesDir, "speech"), NAME);
    }

    public static boolean ready(File filesDir) {
        File f = file(filesDir);
        return f.isFile() && f.length() == SIZE;
    }

    private static File download(File filesDir, String circuit, Progress progress) throws Exception {
        File target = file(filesDir);
        if (ready(filesDir)) return target;
        target.getParentFile().mkdirs();
        File part = new File(target.getPath() + ".part");
        for (int round = 0; round < 2; round++) {
            for (int attempt = 0; attempt < 4; attempt++) {
                try {
                    fetch(circuit, part, progress);
                    break;
                } catch (IOException e) {
                    Diag.mark("speech model download try " + (attempt + 1) + " FAIL " + e.getMessage());
                    if (attempt == 3) throw e;
                    Fetch.rotate(circuit);
                }
            }
            if (SHA256.equals(sha256(part))) {
                if (!part.renameTo(target)) throw new IOException("cannot save model");
                return target;
            }
            Diag.mark("speech model checksum mismatch, downloading again");
            part.delete();
            Fetch.rotate(circuit);
        }
        throw new IOException("model checksum mismatch");
    }

    private static void fetch(String circuit, File part, Progress progress) throws Exception {
        long have = part.isFile() ? part.length() : 0;
        if (have >= SIZE) return;
        Fetch.on(circuit, () -> {
            HttpURLConnection c = Fetch.open(URL);
            c.setRequestProperty("User-Agent", Fetch.USER_AGENT);
            c.setRequestProperty("Accept-Encoding", "identity");
            c.setReadTimeout(30000);
            if (have > 0) c.setRequestProperty("Range", "bytes=" + have + "-");
            int code = c.getResponseCode();
            if (code != 200 && code != 206) throw new IOException("HTTP " + code);
            boolean append = code == 206;
            long done = append ? have : 0;
            try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(part, append)) {
                byte[] buf = new byte[65536];
                long lastReport = 0;
                for (int n = in.read(buf); n > 0; n = in.read(buf)) {
                    out.write(buf, 0, n);
                    done += n;
                    long now = System.currentTimeMillis();
                    if (progress != null && now - lastReport > 250) {
                        lastReport = now;
                        progress.update(done, SIZE);
                    }
                }
            }
            if (done < SIZE) throw new IOException("short download");
            if (progress != null) progress.update(done, SIZE);
            return null;
        });
    }

    private static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new java.io.FileInputStream(f)) {
            byte[] buf = new byte[65536];
            for (int n = in.read(buf); n > 0; n = in.read(buf)) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
