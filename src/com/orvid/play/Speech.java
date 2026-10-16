package com.orvid.play;

import com.orvid.net.Diag;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingDeque;

public final class Speech implements SpeechTap.Sink {

    public interface Listener {
        void segment(long startMs, long endMs, String language, String text);
    }

    private static final int BACKLOG = 2;
    private static final long STOP = Long.MIN_VALUE;

    private static final class Chunk {
        final long startMs;
        final short[] pcm;

        Chunk(long startMs, short[] pcm) {
            this.startMs = startMs;
            this.pcm = pcm;
        }
    }

    private final LinkedBlockingDeque<Chunk> queue = new LinkedBlockingDeque<>();
    private final Listener listener;
    private Process process;
    private volatile boolean running;

    public Speech(Listener listener) {
        this.listener = listener;
    }

    public static boolean available(String nativeDir) {
        return new File(nativeDir, "libwhisper.so").isFile() && new File(nativeDir, "libc++_shared.so").isFile();
    }

    public synchronized void start(String nativeDir, File model, String language) throws IOException {
        if (running) return;
        int threads = Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
        ProcessBuilder pb = new ProcessBuilder(new File(nativeDir, "libwhisper.so").getAbsolutePath(),
                model.getAbsolutePath(), Integer.toString(threads), language == null ? "auto" : language);
        pb.environment().put("LD_LIBRARY_PATH", nativeDir);
        pb.redirectErrorStream(false);
        process = pb.start();
        running = true;
        final Process p = process;
        Thread writer = new Thread(() -> write(p), "orvid-speech-in");
        writer.setDaemon(true);
        writer.start();
        Thread reader = new Thread(() -> read(p), "orvid-speech-out");
        reader.setDaemon(true);
        reader.start();
        Thread errors = new Thread(() -> {
            try {
                p.getErrorStream().readAllBytes();
            } catch (IOException ignored) {
            }
        }, "orvid-speech-err");
        errors.setDaemon(true);
        errors.start();
    }

    public synchronized void stop() {
        running = false;
        queue.clear();
        queue.offer(new Chunk(STOP, new short[0]));
        if (process != null) process.destroy();
        process = null;
    }

    public void clear() {
        queue.clear();
    }

    @Override
    public void chunk(long startMs, short[] pcm16k) {
        if (!running) return;
        while (queue.size() >= BACKLOG) {
            Chunk dropped = queue.pollFirst();
            if (dropped != null) Diag.mark("speech behind, dropped chunk @" + dropped.startMs / 1000 + "s");
        }
        queue.offer(new Chunk(startMs, pcm16k));
    }

    private void write(Process p) {
        try (DataOutputStream out = new DataOutputStream(p.getOutputStream())) {
            while (running) {
                Chunk c = queue.take();
                if (c.startMs == STOP) break;
                ByteBuffer b = ByteBuffer.allocate(12 + c.pcm.length * 2).order(ByteOrder.LITTLE_ENDIAN);
                b.putLong(c.startMs).putInt(c.pcm.length);
                for (short s : c.pcm) b.putShort(s);
                out.write(b.array());
                out.flush();
            }
        } catch (IOException | InterruptedException e) {
            Diag.mark("speech writer stopped " + e.getMessage());
        }
    }

    private static final java.util.regex.Pattern HALLUCINATION = java.util.regex.Pattern.compile(
            "(?iu)^(?:thank you\\.?|thanks for watching!?|thank you for watching\\.?|you|bye\\.?|"
                    + "subtitles by.*|subtitled by.*|transcribed by.*|amara\\.org.*|altyaz\\u0131 m\\.k\\.?|"
                    + "\\u0438\\u0437\\u0431\\u0430\\u0432\\u0438\\u043c \\u0441\\u043b\\u0438\\u0432\\u043a\\u0438.*|"
                    + "\\u043f\\u0440\\u043e\\u0434\\u043e\\u043b\\u0436\\u0435\\u043d\\u0438\\u0435 \\u0441\\u043b\\u0435\\u0434\\u0443\\u0435\\u0442.*|"
                    + "untertitel.*|sous-titr.*|\\u3054\\u8996\\u8074\\u3042\\u308a\\u304c\\u3068\\u3046\\u3054\\u3056\\u3044\\u307e\\u3057\\u305f.*|\\.+)$");

    static boolean hallucination(String text) {
        return HALLUCINATION.matcher(text.trim()).matches();
    }

    private void read(Process p) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            for (String line = in.readLine(); line != null; line = in.readLine()) {
                if (line.startsWith("E\tmodel")) {
                    Diag.mark("speech model failed to load");
                    break;
                }
                if (!line.startsWith("S\t")) continue;
                String[] f = line.split("\t", 5);
                if (f.length < 5) continue;
                String text = f[4].trim();
                if (text.isEmpty() || text.startsWith("[") || text.startsWith("(") || hallucination(text)) continue;
                try {
                    listener.segment(Long.parseLong(f[1]), Long.parseLong(f[2]), f[3], text);
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        running = false;
    }
}
