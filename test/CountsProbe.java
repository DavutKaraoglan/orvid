import com.orvid.search.Counts;
public class CountsProbe {
    public static void main(String[] a) {
        String[][] v = {{"94 Mn görüntüleme","tr"},{"1,8 Mn görüntüleme","tr"},{"12 B görüntüleme","tr"},{"1,2 Mr görüntüleme","tr"},{"903 görüntüleme","tr"},{"1.8M views","en"},{"2.5B views","en"},{"78K views","en"},{"1,234 views","en"}};
        for (String[] x : v) System.out.println(x[0] + " -> " + Counts.views(x[0], x[1]));
        for (String s : new String[]{"6 gün önce","3 yıl önce","11 ay önce","1 hour ago","Streamed 2 weeks ago","5 dakika önce"}) System.out.println(s + " -> " + Counts.ageSeconds(s));
    }
}
