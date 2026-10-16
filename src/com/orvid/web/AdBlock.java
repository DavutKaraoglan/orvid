package com.orvid.web;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

final class AdBlock {

    private static final Set<String> HOSTS = new HashSet<>(Arrays.asList(
            "exoclick.com", "exosrv.com", "exdynsrv.com", "trafficjunky.com", "trafficjunky.net", "trafficfactory.biz",
            "juicyads.com", "magsrv.com", "tsyndicate.com", "realsrv.com", "adsco.re", "sexad.net", "mavrtracktor.com",
            "tcdwm.com", "gorillatraffic.xyz", "hepelu.my", "adtng.com", "mayzaent.com", "zline0.com",
            "smartfloralab.com", "gentlefieldpattern.com", "fontheader.com", "2020mustang.com", "footerfont.com",
            "guppy.live", "gsrv.dev", "jennydanny.com", "popads.net", "popcash.net", "adsterra.com", "a-ads.com",
            "clickadu.com", "hilltopads.net", "propellerads.com", "onclickads.net", "adcash.com", "ero-advertising.com",
            "trafficstars.com", "twinrdsrv.com", "chaturbate.com", "stripchat.com", "livejasmin.com", "bongacams.com",
            "doubleclick.net", "googlesyndication.com", "googletagmanager.com", "google-analytics.com",
            "googleadservices.com", "adnxs.com", "criteo.com", "taboola.com", "outbrain.com", "yandex.ru", "mc.yandex.ru",
            "scorecardresearch.com", "quantserve.com", "hotjar.com", "clarity.ms", "facebook.net", "rtmark.net",
            "pemsrv.com", "dtscdn.com", "dtscout.com", "pushame.com", "notix.io", "aclktrkr.com", "ad-maven.com",
            "adspyglass.com", "aaaconnect.info", "strbh.com", "ptgncdn.com", "bigbang.solutions", "tsyndicate.net", "s.magsrv.com", "a.realsrv.com", "syndication.realsrv.com"));

    private AdBlock() {
    }

    static boolean blocked(String host) {
        if (host == null) return false;
        String h = host.toLowerCase();
        while (true) {
            if (HOSTS.contains(h)) return true;
            int dot = h.indexOf('.');
            if (dot < 0 || h.indexOf('.', dot + 1) < 0) return false;
            h = h.substring(dot + 1);
        }
    }

    static boolean sameSite(String a, String b) {
        if (a == null || b == null) return false;
        return base(a).equals(base(b));
    }

    private static String base(String host) {
        String[] p = host.toLowerCase().split("\\.");
        if (p.length < 2) return host;
        return p[p.length - 2] + "." + p[p.length - 1];
    }
}
