import com.orvid.search.Filters;
public class ParamsProbe {
    public static void main(String[] a) {
        Filters f = new Filters();
        int[][] cases = {{0,0,0},{1,0,0},{2,0,0},{1,3,0},{0,3,3},{2,3,3}};
        for (int[] c : cases) { f.type = c[0]; f.duration = c[1]; f.sort = c[2]; System.out.println("type=" + c[0] + " dur=" + c[1] + " sort=" + c[2] + " -> " + f.youtubeParams() + "  dm=" + f.dailymotionParams()); }
    }
}
