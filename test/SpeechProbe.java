import com.orvid.net.*;
import com.orvid.tor.Tor;
import com.orvid.play.*;
import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import java.io.File;
import java.nio.*;
public class SpeechProbe {
    public static void main(String[] a) throws Exception {
        Fetch.setTorMode(true);
        File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
        Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
        System.out.println("tor " + Tor.get().awaitReady(180000));
        byte[] wav = java.nio.file.Files.readAllBytes(new File("tools/whisper/src/samples/jfk.wav").toPath());
        ShortBuffer mono = ByteBuffer.wrap(wav, 44, wav.length - 44).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        int n = mono.remaining();
        int outN = (int) ((long) n * 44100 / 16000);
        short[] stereo = new short[outN * 2];
        for (int i = 0; i < outN; i++) { short v = mono.get((int) ((long) i * 16000 / 44100)); stereo[2 * i] = v; stereo[2 * i + 1] = v; }
        String circuit = Fetch.newCircuit("tr");
        long t0 = System.currentTimeMillis();
        Speech speech = new Speech((s, e, lang, text) -> {
            String tr = Translate.text(circuit, text, lang, "tr");
            System.out.printf("[%5.1fs-%5.1fs] %s | %s  (+%dms)%n", s / 1000.0, e / 1000.0, text, tr, System.currentTimeMillis() - t0);
        });
        System.out.println("available " + Speech.available("libs/native/arm64-v8a"));
        speech.start(new File("libs/native/arm64-v8a").getAbsolutePath(), new File("tools/whisper/models/ggml-base-q5_1.bin"), "auto");
        SpeechTap tap = new SpeechTap();
        tap.configure(new AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT));
        tap.flush(new AudioProcessor.StreamMetadata(30_000_000L));
        tap.setSink(speech);
        for (int rep = 0; rep < 2; rep++) {
            for (int off = 0; off < outN; off += 4096) {
                int frames = Math.min(4096, outN - off);
                ByteBuffer b = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.LITTLE_ENDIAN);
                for (int i = 0; i < frames * 2; i++) b.putShort(stereo[off * 2 + i]);
                b.flip();
                tap.queueInput(b);
                tap.getOutput();
            }
        }
        Thread.sleep(25000);
        speech.stop();
        Tor.get().stop(); Thread.sleep(300); System.exit(0);
    }
}
