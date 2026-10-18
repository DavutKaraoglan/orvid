import org.mozilla.javascript.*;
import java.nio.file.*;
import java.util.*;
public class RhinoCost {
    public static void main(String[] a) throws Exception {
        List<String> codes = new ArrayList<>();
        for (String f : a) codes.add(new String(Files.readAllBytes(Paths.get(f))));
        for (int round = 0; round < 3; round++) {
            long t = System.nanoTime();
            for (String code : codes) {
                Context cx = Context.enter();
                try { cx.setInterpretedMode(true); ScriptableObject scope = cx.initSafeStandardObjects(); cx.evaluateString(scope, code, "s", 1, null); } finally { Context.exit(); }
            }
            long eval = System.nanoTime() - t;
            List<Script> compiled = new ArrayList<>();
            Context cx0 = Context.enter(); cx0.setInterpretedMode(true);
            for (String code : codes) compiled.add(cx0.compileString(code, "s", 1, null));
            Context.exit();
            t = System.nanoTime();
            for (Script s : compiled) {
                Context cx = Context.enter();
                try { cx.setInterpretedMode(true); ScriptableObject scope = cx.initSafeStandardObjects(); s.exec(cx, scope); } finally { Context.exit(); }
            }
            long exec = System.nanoTime() - t;
            System.out.printf("round %d: evaluate all %d ms, exec precompiled %d ms%n", round, eval / 1000000, exec / 1000000);
        }
    }
}
