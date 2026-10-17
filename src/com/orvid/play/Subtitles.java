package com.orvid.play;

import android.content.Context;

import com.orvid.R;
import com.orvid.yt.Visionos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Subtitles {

    private Subtitles() {
    }

    public static List<Visionos.Caption> withTranslation(Context context, List<Visionos.Caption> captions) {
        List<Visionos.Caption> all = new ArrayList<>(captions);
        String mine = Locale.getDefault().getLanguage();
        Visionos.Caption base = null;
        for (Visionos.Caption c : captions) {
            if (c.language.equals(mine) || c.language.startsWith(mine + "-")) return all;
            if (base == null || base.auto && !c.auto) base = c;
        }
        if (base != null && !com.orvid.net.Fetch.torMode()) {
            Visionos.Caption t = new Visionos.Caption();
            t.language = mine;
            t.name = context.getString(R.string.subtitle_translated,
                    Locale.getDefault().getDisplayLanguage(Locale.getDefault()));
            t.url = base.url + "&tlang=" + mine;
            all.add(t);
        }
        return all;
    }
}
