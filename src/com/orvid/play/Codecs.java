package com.orvid.play;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;

public final class Codecs {

    private static int vp9;
    private static boolean probed;

    private Codecs() {
    }

    public static synchronized int vp9MaxHeight() {
        if (probed) return vp9;
        probed = true;
        int[][] sizes = {{3840, 2160}, {2560, 1440}, {1920, 1080}};
        for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
            if (info.isEncoder()) continue;
            if (Build.VERSION.SDK_INT >= 29 && !info.isHardwareAccelerated()) continue;
            if (Build.VERSION.SDK_INT < 29 && info.getName().startsWith("OMX.google.")) continue;
            for (String type : info.getSupportedTypes()) {
                if (!type.equalsIgnoreCase("video/x-vnd.on2.vp9")) continue;
                MediaCodecInfo.VideoCapabilities caps = info.getCapabilitiesForType(type).getVideoCapabilities();
                if (caps == null) continue;
                for (int[] s : sizes) {
                    if (caps.isSizeSupported(s[0], s[1]) || caps.isSizeSupported(s[1], s[0])) {
                        vp9 = Math.max(vp9, s[1]);
                        break;
                    }
                }
            }
        }
        return vp9;
    }
}
