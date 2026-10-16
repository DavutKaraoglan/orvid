import com.orvid.net.*;
import com.orvid.tor.Tor;
import com.orvid.vk.Vk;
import java.io.File;
public class VkProbe {
    public static void main(String[] a) throws Exception {
        Fetch.setTorMode(true);
        File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
        Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
        System.out.println("tor ready " + Tor.get().awaitReady(180000));
        String c = Fetch.newCircuit("vk");
        long t = System.currentTimeMillis();
        Vk.Page p = Fetch.retrying(c, cc -> Vk.search(cc, "barış manço", null));
        System.out.println("page1 " + p.items.size() + " in " + (System.currentTimeMillis() - t) + "ms next=" + (p.next != null));
        for (int i = 0; i < Math.min(3, p.items.size()); i++) System.out.println("  " + p.items.get(i).getName() + " | " + p.items.get(i).getUploaderName() + " | " + p.items.get(i).getDuration() + "s | " + p.items.get(i).getViewCount());
        if (p.next != null) {
            final String nx = p.next;
            Vk.Page p2 = Fetch.retrying(c, cc -> Vk.search(cc, "barış manço", nx));
            System.out.println("page2 " + p2.items.size() + " first=" + (p2.items.isEmpty() ? "-" : p2.items.get(0).getName()) + " same-as-page1=" + (!p2.items.isEmpty() && p2.items.get(0).getUrl().equals(p.items.get(0).getUrl())));
        }
        String id = Vk.videoId(p.items.get(0).getUrl());
        String c2 = Fetch.newCircuit("vk2");
        Vk.Video v = Fetch.retrying(c2, cc -> Vk.fetch(cc, id));
        System.out.println("video.get " + v.title + " files=" + v.files.keySet());
        System.out.print(Diag.dump());
        Tor.get().stop(); Thread.sleep(1500); System.exit(0);
    }
}
