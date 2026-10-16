import java.nio.file.*;
public class XhVectors {
    public static void main(String[] a) throws Exception {
        Class<?> bytes = Class.forName("com.orvid.xh.XHamster$Bytes");
        java.lang.reflect.Constructor<?> ctor = bytes.getDeclaredConstructor(int.class, int.class);
        ctor.setAccessible(true);
        java.lang.reflect.Method next = bytes.getDeclaredMethod("next");
        next.setAccessible(true);
        int ok = 0, bad = 0;
        for (String line : Files.readAllLines(Paths.get(a[0]))) {
            String[] p = line.split(" ");
            Object g = ctor.newInstance(Integer.parseInt(p[0]), Integer.parseInt(p[1]));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 64; i++) sb.append(String.format("%02x", (Integer) next.invoke(g)));
            if (sb.toString().equals(p[2])) ok++; else { bad++; if (bad < 4) System.out.println("mismatch algo " + p[0]); }
        }
        System.out.println("vectors ok=" + ok + " bad=" + bad);
    }
}
