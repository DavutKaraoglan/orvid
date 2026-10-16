package com.orvid.play;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.BaseAudioProcessor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class SpeechTap extends BaseAudioProcessor {

    public interface Sink {
        void chunk(long startMs, short[] pcm16k);
    }

    public static final int RATE = 16000;
    private static final int MAX = RATE * 8;
    private static final int MIN_CUT = RATE * 5;
    private static final int WINDOW = RATE / 10;

    private volatile Sink sink;
    private short[] buffer = new short[MAX];
    private int filled;
    private long baseMs;
    private long outFrames;
    private long baseFrame;
    private double phase;

    public void setSink(Sink s) {
        sink = s;
        filled = 0;
    }

    @Override
    protected AudioFormat onConfigure(AudioFormat input) throws UnhandledAudioFormatException {
        if (input.encoding != C.ENCODING_PCM_16BIT) throw new UnhandledAudioFormatException(input);
        return input;
    }

    @Override
    public void queueInput(ByteBuffer input) {
        int size = input.remaining();
        if (size == 0) return;
        Sink s = sink;
        if (s != null) capture(input.duplicate().order(ByteOrder.LITTLE_ENDIAN), s);
        ByteBuffer out = replaceOutputBuffer(size);
        out.put(input);
        out.flip();
    }

    private void capture(ByteBuffer in, Sink s) {
        int channels = inputAudioFormat.channelCount;
        int rate = inputAudioFormat.sampleRate;
        if (channels <= 0 || rate <= 0) return;
        double step = (double) rate / RATE;
        int frames = in.remaining() / (2 * channels);
        int start = in.position();
        while (phase < frames) {
            int f = (int) phase;
            int sum = 0;
            for (int c = 0; c < channels; c++) sum += in.getShort(start + (f * channels + c) * 2);
            if (filled == 0) baseFrame = outFrames;
            buffer[filled++] = (short) (sum / channels);
            outFrames++;
            if (filled == MAX) emit(s);
            phase += step;
        }
        phase -= frames;
    }

    private void emit(Sink s) {
        int cut = quietest();
        short[] done = new short[cut];
        System.arraycopy(buffer, 0, done, 0, cut);
        long startMs = baseMs + baseFrame * 1000 / RATE;
        int rest = filled - cut;
        short[] next = new short[MAX];
        System.arraycopy(buffer, cut, next, 0, rest);
        buffer = next;
        filled = rest;
        baseFrame += cut;
        s.chunk(startMs, done);
    }

    private int quietest() {
        int best = filled;
        long bestEnergy = Long.MAX_VALUE;
        for (int at = MIN_CUT; at + WINDOW <= filled; at += WINDOW / 2) {
            long e = 0;
            for (int i = at; i < at + WINDOW; i++) e += (long) buffer[i] * buffer[i];
            if (e < bestEnergy) {
                bestEnergy = e;
                best = at + WINDOW / 2;
            }
        }
        return best;
    }

    @Override
    protected void onFlush(AudioProcessor.StreamMetadata meta) {
        long offset = meta == null ? C.TIME_UNSET : meta.positionOffsetUs;
        baseMs = offset == C.TIME_UNSET ? 0 : offset / 1000;
        outFrames = 0;
        baseFrame = 0;
        filled = 0;
        phase = 0;
    }

    @Override
    protected void onReset() {
        filled = 0;
        outFrames = 0;
        baseFrame = 0;
        baseMs = 0;
        phase = 0;
    }
}
