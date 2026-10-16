package com.orvid.play;

import com.orvid.yt.Visionos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Dash {

    private static final int MIN_HEIGHT = 360;

    private Dash() {
    }

    public static List<Visionos.Format> videoTracks(List<Visionos.Format> formats, int maxHeight, int vp9Max) {
        List<Visionos.Format> vp9 = new ArrayList<>();
        List<Visionos.Format> avc = new ArrayList<>();
        for (Visionos.Format f : formats) {
            if (!f.isVideo() || f.initRange == null || f.indexRange == null) continue;
            String codec = f.codec();
            if (f.mime.startsWith("video/webm") && (codec.equals("vp9") || codec.startsWith("vp09.00"))) {
                if (f.height <= Math.min(maxHeight, vp9Max)) vp9.add(f);
            } else if (f.mime.startsWith("video/mp4") && codec.startsWith("avc1") && f.height <= Math.min(maxHeight, 1080)) {
                avc.add(f);
            }
        }
        List<Visionos.Format> out = vp9Max >= 1080 && !vp9.isEmpty() ? vp9 : avc;
        List<Visionos.Format> sharp = new ArrayList<>();
        for (Visionos.Format f : out) if (f.height >= MIN_HEIGHT) sharp.add(f);
        return sharp.isEmpty() ? out : sharp;
    }

    public static Visionos.Format audioTrack(List<Visionos.Format> formats) {
        Visionos.Format best = null;
        int bestScore = -1;
        for (Visionos.Format f : formats) {
            if (!f.mime.startsWith("audio/mp4") || f.initRange == null || f.indexRange == null) continue;
            int score = (f.isOriginalAudio() ? 4 : 0) + (f.audioDefault ? 2 : 0) + (f.itag == 140 ? 1 : 0);
            if (score > bestScore || score == bestScore && f.bitrate > best.bitrate) {
                best = f;
                bestScore = score;
            }
        }
        return best;
    }

    public static List<Visionos.Format> audioTracks(List<Visionos.Format> formats) {
        java.util.Map<String, Visionos.Format> byTrack = new java.util.LinkedHashMap<>();
        for (Visionos.Format f : formats) {
            if (!f.mime.startsWith("audio/mp4") || f.initRange == null || f.indexRange == null) continue;
            String key = f.audioTrackId == null ? "" : f.audioTrackId;
            Visionos.Format known = byTrack.get(key);
            if (known == null || f.itag == 140 && known.itag != 140
                    || known.itag != 140 && f.bitrate > known.bitrate) {
                byTrack.put(key, f);
            }
        }
        List<Visionos.Format> out = new ArrayList<>(byTrack.values());
        java.util.Collections.sort(out, (a, b) -> Boolean.compare(b.isOriginalAudio(), a.isOriginalAudio()));
        return out;
    }

    public static String manifest(List<Visionos.Format> video, List<Visionos.Format> audios,
                                  List<Visionos.Caption> captions) {
        Visionos.Format audio = audios.get(0);
        long durationMs = audio.durationMs;
        for (Visionos.Format f : video) durationMs = Math.max(durationMs, f.durationMs);
        StringBuilder sb = new StringBuilder(4096)
                .append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\" type=\"static\" ")
                .append("profiles=\"urn:mpeg:dash:profile:isoff-on-demand:2011\" minBufferTime=\"PT1S\" ")
                .append("mediaPresentationDuration=\"PT").append(String.format(Locale.ROOT, "%.3f", durationMs / 1000.0))
                .append("S\">\n<Period>\n")
                .append("<AdaptationSet id=\"0\" contentType=\"video\" mimeType=\"")
                .append(video.get(0).mime.startsWith("video/webm") ? "video/webm" : "video/mp4")
                .append("\" subsegmentAlignment=\"true\">\n");
        for (Visionos.Format f : video) {
            sb.append("<Representation id=\"").append(f.itag)
                    .append("\" codecs=\"").append(f.codec())
                    .append("\" bandwidth=\"").append(Math.max(1, f.averageBitrate))
                    .append("\" width=\"").append(f.width)
                    .append("\" height=\"").append(f.height);
            if (f.fps > 0) sb.append("\" frameRate=\"").append(f.fps);
            sb.append("\">");
            segment(sb, f);
            sb.append("</Representation>\n");
        }
        sb.append("</AdaptationSet>\n");
        int id = 1;
        for (Visionos.Format a : audios) {
            sb.append("<AdaptationSet id=\"").append(id++)
                    .append("\" contentType=\"audio\" mimeType=\"audio/mp4\" subsegmentAlignment=\"true\"");
            if (a.audioTrackId != null) {
                String lang = a.audioTrackId.substring(0, Math.max(0, a.audioTrackId.indexOf('.')));
                if (!lang.isEmpty()) sb.append(" lang=\"").append(escape(lang)).append('"');
            }
            sb.append(">\n<Role schemeIdUri=\"urn:mpeg:dash:role:2011\" value=\"")
                    .append(a.isOriginalAudio() ? "main" : "dub").append("\"/>\n");
            if (a.audioTrackName != null) sb.append("<Label>").append(escape(a.audioTrackName)).append("</Label>\n");
            sb.append("<Representation id=\"a").append(a.itag).append('-').append(id)
                    .append("\" codecs=\"").append(a.codec())
                    .append("\" bandwidth=\"").append(Math.max(1, a.averageBitrate));
            if (a.sampleRate > 0) sb.append("\" audioSamplingRate=\"").append(a.sampleRate);
            sb.append("\">");
            segment(sb, a);
            sb.append("</Representation>\n</AdaptationSet>\n");
        }
        for (Visionos.Caption c : captions) {
            sb.append("<AdaptationSet id=\"").append(id++)
                    .append("\" contentType=\"text\" mimeType=\"text/vtt\" lang=\"").append(escape(c.language))
                    .append("\">\n<Label>").append(escape(c.name)).append("</Label>\n")
                    .append("<Representation id=\"t").append(id).append("\" bandwidth=\"256\">")
                    .append("<BaseURL>").append(escape(c.url)).append("</BaseURL>")
                    .append("</Representation>\n</AdaptationSet>\n");
        }
        sb.append("</Period>\n</MPD>\n");
        return sb.toString();
    }

    private static void segment(StringBuilder sb, Visionos.Format f) {
        sb.append("<BaseURL>").append(escape(f.url)).append("</BaseURL>")
                .append("<SegmentBase indexRange=\"").append(f.indexRange).append("\">")
                .append("<Initialization range=\"").append(f.initRange).append("\"/>")
                .append("</SegmentBase>");
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
