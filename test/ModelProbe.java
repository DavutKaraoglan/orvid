import com.orvid.net.*;
import com.orvid.tor.Tor;
import com.orvid.play.SpeechModel;
import java.io.File;
import java.util.concurrent.*;
public class ModelProbe {
    public static void main(String[] a) throws Exception {
        Fetch.setTorMode(true);
        File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
        Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
        System.out.println("tor " + Tor.get().awaitReady(180000));
        File files = new File(System.getenv("PREFIX") + "/tmp/model-test");
        files.mkdirs();
        CountDownLatch done = new CountDownLatch(2);
        long t0 = System.currentTimeMillis();
        for (String name : new String[]{"ekran1", "ekran2"}) {
            int[] last = {-10};
            SpeechModel.ensure(files, Fetch.newCircuit("m"), new SpeechModel.Listener() {
                public void update(long d, long total) { int p = (int) (d * 100 / total); if (p >= last[0] + 20) { last[0] = p; System.out.println(name + " %" + p + " " + (System.currentTimeMillis() - t0) / 1000 + "s"); } }
                public void finished(boolean ok) { System.out.println(name + " finished ok=" + ok + " ready=" + SpeechModel.ready(files) + " " + (System.currentTimeMillis() - t0) / 1000 + "s"); done.countDown(); }
            });
            Thread.sleep(3000);
        }
        done.await(20, TimeUnit.MINUTES);
        long threads = Thread.getAllStackTraces().keySet().stream().filter(t -> t.getName().equals("orvid-speech-model")).count();
        System.out.println("indirme thread sayisi (bitince 0 olmali): " + threads);
        Tor.get().stop(); Thread.sleep(300); System.exit(0);
    }
}
