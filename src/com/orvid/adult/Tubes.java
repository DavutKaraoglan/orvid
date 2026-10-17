package com.orvid.adult;

public final class Tubes {

    private Tubes() {
    }

    public static final HtmlTube HELLPORNO = new HtmlTube(114, "https://hellporno.com",
            "https://hellporno.com/search/?q={q}", "https://hellporno.com/search/{p}/?q={q}",
            "hellporno\\.com/videos/[^\\s\"?#]+/", "class=\"video-thumb\"",
            "href=\"(https://hellporno\\.com/videos/[^\"]+/)\"", "class=\"title\">([^<]+)<",
            "<img alt=\"[^\"]*\" src=\"([^\"]+)\"", "class=\"time\">([0-9:]+)<", "class=\"info\"><span>(\\d+)");

    public static final HtmlTube PORNHAT = new HtmlTube(115, "https://www.pornhat.com",
            "https://www.pornhat.com/search/{qp}/", "https://www.pornhat.com/search/{qp}/{p}/",
            "pornhat\\.com/video/[^\\s\"?#]+/", "class=\"item thumb-bl",
            "href=\"((?:https://www\\.pornhat\\.com)?/video/[^\"]+/)\"", "href=\"[^\"]*\" title=\"([^\"]+)\"",
            "data-original=\"([^\"]+)\"", ">\\s*(\\d{1,2}:\\d{2}(?::\\d{2})?)\\s*<", null);

    public static final HtmlTube BRAVOTUBE = new HtmlTube(116, "https://www.bravotube.net",
            "https://www.bravotube.net/search/?q={q}", "https://www.bravotube.net/search/{p}/?q={q}",
            "bravotube\\.net/videos/[^\\s\"?#]+/", "class=\"video_block_",
            "href=\"((?:https://www\\.bravotube\\.net)?/videos/[^\"]+/)\"", "alt=\"([^\"]+)\"",
            "<img src=\"([^\"]+)\"", "class=\"time\">([0-9:]+)<", null);

    public static final HtmlTube HQPORNER = new HtmlTube(117, "https://hqporner.com",
            "https://hqporner.com/?q={q}", "https://hqporner.com/?q={q}&p={p}",
            "hqporner\\.com/hdporn/[^\\s\"?#]+\\.html", "<section class=\"box feature\">",
            "href=\"(/hdporn/[^\"]+\\.html)\"", "class=\"meta-data-title\"><a [^>]*>([^<]+)<",
            "defaultImage\\(\"([^\"]+)\"", "fa-clock-o meta-data\">([^<]+)<", null)
            .iframe("<iframe[^>]+src=\"((?:https?:)?//[^\"]*mydaddy\\.cc/video/[^\"]+)\"");

    public static final HtmlTube EROME = new HtmlTube(118, "https://www.erome.com",
            "https://www.erome.com/search?q={q}", "https://www.erome.com/search?q={q}&page={p}",
            "erome\\.com/a/[A-Za-z0-9]+", "id=\"album-",
            "href=\"(https://www\\.erome\\.com/a/[A-Za-z0-9]+)\"", "<img [^>]*alt=\"([^\"]+)\"",
            "class=\"album-thumbnail[^\"]*\" src=\"([^\"]+)\"", null, "fa-eye\"></use></svg>\\s*([0-9.,]+[KkMm]?)")
            .only("album-videos").referer("https://www.erome.com/");

    public static final HtmlTube PORNGO = new HtmlTube(119, "https://www.porngo.com",
            "https://www.porngo.com/search/{qp}/", "https://www.porngo.com/search/{qp}/{p}/",
            "porngo\\.com/videos/\\d+/[^\\s\"?#]+", "class=\"thumb item",
            "href=\"(https://www\\.porngo\\.com/videos/\\d+/[^\"]+)\"", "<img src=\"[^\"]+\" alt=\"([^\"]+)\"",
            "<img src=\"([^\"]+)\"", "thumb__duration\">([^<]+)<", null);

    public static final HtmlTube GAYPORNTUBE = new HtmlTube(120, "https://www.gayporntube.com",
            "https://www.gayporntube.com/search/?q={q}", "https://www.gayporntube.com/search/?q={q}&page={p}",
            "gayporntube\\.com/video/\\d+/[^\\s\"?#]+", "class=\"item item-col\"",
            "href=\"(https://www\\.gayporntube\\.com/video/\\d+/[^\"]+)\"", "title=\"([^\"]+)\"",
            "data-src=\"([^\"]+)\"", ">\\s*(\\d{1,2}:\\d{2}(?::\\d{2})?)\\s*<", null);

    public static final HtmlTube RULE34VIDEO = new HtmlTube(121, "https://rule34video.com",
            "https://rule34video.com/search/{qp}/", "https://rule34video.com/search/{qp}/{p}/",
            "rule34video\\.com/video/\\d+/[^\\s\"?#]+", "class=\"item thumb video_",
            "href=\"(https://rule34video\\.com/video/\\d+/[^\"]+)\"", "title=\"([^\"]+)\"",
            "<img class=\"thumb\" src=\"([^\"]+)\"", ">\\s*(\\d{1,2}:\\d{2}(?::\\d{2})?)\\s*<", null);

    public static final HtmlTube PORNICOM = new HtmlTube(122, "https://www.pornicom.com",
            "https://www.pornicom.com/search/?q={q}", "https://www.pornicom.com/search/{p}/?q={q}",
            "pornicom\\.com/videos/\\d+/[^\\s\"?#]+", "class=\"img-holder image\"",
            "href=\"(https://www\\.pornicom\\.com/videos/\\d+/[^\"]+)\"", "<img class=\"thumb\" src=\"[^\"]*\" alt=\"([^\"]+)\"",
            "<img class=\"thumb\" src=\"([^\"]+)\"", "class=\"time\">\\s*([0-9:]+)", null);

    public static final HtmlTube XXXYMOVIES = new HtmlTube(123, "https://xxxymovies.com",
            "https://xxxymovies.com/search/{qp}/", "https://xxxymovies.com/search/{qp}/{p}/",
            "xxxymovies\\.com/videos/\\d+/[^\\s\"?#]+", "<div class=\"item ",
            "href=\"(https://xxxymovies\\.com/videos/\\d+/[^\"]+)\"", "title=\"([^\"]+)\"",
            "data-original=\"([^\"]+)\"", ">\\s*(\\d{1,2}:\\d{2}(?::\\d{2})?)\\s*<", null);

    public static final HtmlTube ZBPORN = new HtmlTube(124, "https://zbporn.com",
            "https://zbporn.com/search/{qp}/", "https://zbporn.com/search/{qp}/{p}/",
            "zbporn\\.com/videos/\\d+/[^\\s\"?#]+", "class=\"th-image\"",
            "href=\"(https://zbporn\\.com/videos/\\d+/[^\"]+)\"", "title=\"([^\"]+)\"",
            "type=\"image/jpeg\" srcset=\"([^\" ]+)", ">\\s*(\\d{1,2}:\\d{2}(?::\\d{2})?)\\s*<", null);

    public static final HtmlTube DRTUBER = new HtmlTube(125, "https://www.drtuber.com",
            "https://www.drtuber.com/search/videos/{qp}", "https://www.drtuber.com/search/videos/{qp}/{p}",
            "drtuber\\.com/video/\\d+/", "class=\"th ch-video",
            "href=\"(/video/\\d+/[^\"]+)\"", "alt=\"([^\"]+)\"",
            "<img [^>]*src=\"(https://[^\"]+)\"", "time_thumb\">\\s*<em>([0-9:]+)<", null).web();

    public static final HtmlTube PORNDOE = new HtmlTube(126, "https://porndoe.com",
            "https://porndoe.com/search?keywords={q}", "https://porndoe.com/search?keywords={q}&page={p}",
            "porndoe\\.com/watch/[a-z0-9]+", "class=\"video-item\" data-video-item",
            "href=\"(/watch/[a-z0-9]+)\"", "aria-label=\"([^\"]+)\"",
            "data-src=\"(https://[^\"]+)\"", "data-duration=\"([0-9:]+)\"", null).web();

    public static final HtmlTube THISVID = new HtmlTube(127, "https://thisvid.com",
            "https://thisvid.com/search/?q={q}", "https://thisvid.com/search/{p}/?q={q}",
            "thisvid\\.com/videos/[^\\s\"?#]+", "<a href=\"https://thisvid.com/videos/",
            "^([^\"]+)\"", "title=\"([^\"]+)\"",
            "data-original=\"([^\"]+)\"", "class=\"duration\">\\s*([0-9:]+)", ">(\\d+)</span><span class=\"date")
            .skip("thumb private").linkPrefix("https://thisvid.com/videos/").web();

    public static final HtmlTube FREEPORN8 = new HtmlTube(153, "https://www.freeporn8.com",
            "https://www.freeporn8.com/search/?q={q}", "https://www.freeporn8.com/search/?q={q}&from_videos={p}",
            "freeporn8\\.com/videos/\\d+/[^\\s\"?#]+", "<div class=\"item  \">",
            "href=\"(https://www\\.freeporn8\\.com/videos/\\d+/[^\"]+/)\"", "title=\"([^\"]+)\"",
            "src=\"(https://www\\.freeporn8\\.com/contents/videos_screenshots/[^\"]+)\"", "class=\"duration\">([0-9:]+)<", null)
            .paged();

    public static final HtmlTube PORNWHITE = new HtmlTube(154, "https://www.pornwhite.com",
            "https://www.pornwhite.com/search/?q={q}", "https://www.pornwhite.com/search/{p}/?q={q}",
            "pornwhite\\.com/videos/\\d+/[^\\s\"?#]+", "itemtype=\"https://schema.org/VideoObject\"",
            "href=\"(https://www\\.pornwhite\\.com/videos/\\d+/[^\"]+/)\"", "itemprop=\"name\" content=\"([^\"]+)\"",
            "data-original=\"([^\"]+)\"", "class=\"length\">([0-9:]+)<", "class=\"views\">([0-9.,KkMm]+)<");

    public static final HtmlTube SHOOSHTIME = new HtmlTube(155, "https://shooshtime.com",
            "https://shooshtime.com/search/?q={q}", "https://shooshtime.com/search/?q={q}&from_videos={p}",
            "shooshtime\\.com/videos/\\d+/[^\\s\"?#]+", "<div class=\"item custom-sort-",
            "href=\"(https://shooshtime\\.com/videos/\\d+/[^\"]+/)\"", "title=\"([^\"]+)\"",
            "data-src=\"([^\"]+)\"", "class=\"duration\">\\s*([0-9:]+)", null)
            .paged();

    public static final HtmlTube THREEMOVS = new HtmlTube(156, "https://www.3movs.com",
            "https://www.3movs.com/search_videos/?q={q}", "https://www.3movs.com/search_videos/?q={q}&from_videos={p}",
            "3movs\\.com/videos/\\d+/[^\\s\"?#]+", "<div class=\"item thumb ",
            "href=\"(https://www\\.3movs\\.com/videos/\\d+/[^\"]+/)\"", "title=\"([^\"]+)\"",
            "data-src=\"([^\"]+)\"", "class=\"time\">([0-9:]+)<", "icon-eye\"></i>\\s*<span>([0-9.,KkMm]+)<")
            .paged();

    public static final HtmlTube PERFECTGIRLS = new HtmlTube(157, "https://www.perfectgirls.xxx",
            "https://www.perfectgirls.xxx/search/{qp}/", "https://www.perfectgirls.xxx/search/{qp}/{p}/",
            "perfectgirls\\.xxx/video/\\d+/", "item thumb-bl thumb-bl-video",
            "href=\"(/video/\\d+/)\"", "title=\"([^\"]+)\"",
            "data-original=\"([^\"]+)\"", "fa-clock-o\"></i>\\s*<span>([0-9:]+)<", "fa-eye\"></i>\\s*<span>([0-9.,KkMm]+)<");

    public static final HtmlTube YOUJIZZ = new HtmlTube(159, "https://www.youjizz.com",
            "https://www.youjizz.com/search/{qp}-1.html", "https://www.youjizz.com/search/{qp}-{p}.html",
            "youjizz\\.com/videos/[^\"'\\s?#]+-\\d+\\.html", "class=\"video-thumb\"",
            "href=\"(/videos/[^\"]+\\.html)\"", "class=\"agego-content-title\">([^<]+)<",
            "data-original=\"([^\"]+)\"", "fa-clock-o\"></i>&nbsp;([0-9:]+)<", "class=\"format-views\">([0-9]+)<");

    public static final HtmlTube PLAYVIDS = new HtmlTube(160, "https://www.playvids.com",
            "https://www.playvids.com/videos?qry={q}", "https://www.playvids.com/videos?qry={q}&page={p}",
            "playvids\\.com/[A-Za-z0-9_-]{11}/[a-z0-9-]+", "class=\"card thumbs_rotate",
            "href=\"(/[A-Za-z0-9_-]{11}/[a-z0-9-]+)\"", "alt=\"([^\"]+)\"",
            "src=\"(https://cdn-img1\\.playvids\\.com/[^\"]+)\"", "class=\"duration\">([0-9:]+)<", null);

    public static final HtmlTube XFIGHTS = new HtmlTube(161, "https://xfights.to",
            "https://xfights.to/search/{qp}/", "https://xfights.to/search/{qp}/?from_videos={p}",
            "xfights\\.to/videos/\\d+/", "itemtype=\"http://schema.org/Movie\"",
            "href=\"(https://xfights\\.to/videos/\\d+/[^\"]+)\"", "title=\"([^\"]+)\"",
            "src=\"(https://cdn\\.xfights\\.to/contents/videos_screenshots/[^\"]+)\"",
            "data-duration=\"([0-9:]+)\"", "data-views=\"([0-9.,KkMm]+)\"");

    public static final HtmlTube FEMDOMVC = new HtmlTube(162, "https://www.femdomvc.com",
            "https://www.femdomvc.com/search/?q={q}", "https://www.femdomvc.com/search/?q={q}&from_videos={p}",
            "femdomvc\\.com/video/\\d+/", "<div class=\"item ",
            "href=\"(https://www\\.femdomvc\\.com/video/\\d+/[^\"]+)\"", "title=\"([^\"]+)\"",
            "data-original=\"([^\"]+)\"", "class=\"duration\">([0-9:]+)<", "class=\"views\">([0-9.,KkMm]+)<");

    public static final HtmlTube ANYSEX = new HtmlTube(163, "https://anysex.com",
            "https://anysex.com/search/?q={q}", "https://anysex.com/search/?q={q}&from_videos={p}",
            "anysex\\.com/video/\\d+/", "<div class=\"item",
            "href=\"(https://anysex\\.com/video/\\d+/[^\"]+)\"", "alt=\"([^\"]+)\"",
            "src=\"(https://i\\.anysex\\.com/videos_screenshots/[^\"]+)\"", "class=\"duration\"[^>]*>\\s*([0-9:]+)", "data-views=\"([0-9]+)\"");

    public static final HtmlTube DAFREEPORN = new HtmlTube(164, "https://www.dafreeporn.com",
            "https://www.dafreeporn.com/search/{qp}/", "https://www.dafreeporn.com/search/{qp}/?from_videos={p}",
            "dafreeporn\\.com/videos/\\d+/", "<div class=\"item",
            "href=\"(https://www\\.dafreeporn\\.com/videos/\\d+/[^\"]+)\"", "title=\"([^\"]+)\"",
            "src=\"(https://www\\.dafreeporn\\.com/contents/videos_screenshots/[^\"]+)\"", "class=\"duration\">([0-9:]+)<", null);

    public static final HtmlTube JUICYVID = new HtmlTube(165, "https://juicyvid.com",
            "https://juicyvid.com/search/{qp}/", "https://juicyvid.com/search/{qp}/?from_videos={p}",
            "juicyvid\\.com/video/\\d+/", "<div class=\"item",
            "href=\"(https://juicyvid\\.com/video/\\d+/[^\"]+)\"", "title=\"([^\"]+)\"",
            "data-original=\"([^\"]+)\"", "class=\"duration\">([0-9:]+)<", "class=\"views\">([0-9.,KkMm]+)<");

    public static final HtmlTube MEGATUBE = new HtmlTube(166, "https://www.megatube.xxx",
            "https://www.megatube.xxx/search/{qp}/", "https://www.megatube.xxx/search/{qp}/?from_videos={p}",
            "megatube\\.xxx/videos/\\d+/", "<div class=\"item",
            "href=\"(https://www\\.megatube\\.xxx/videos/\\d+/[^\"]+)\"", "<a href=\"[^\"]+\"\\s+title=\"([^\"]+)\"",
            "src=\"(https://img01\\.mt-static\\.com/[^\"]+)\"", null, null);

    public static final HtmlTube SORTPORN = new HtmlTube(168, "https://www.sortporn.com",
            "https://www.sortporn.com/search/{qp}/", "https://www.sortporn.com/search/{qp}/?from_videos={p}",
            "sortporn\\.com/videos/\\d+/", "<div class=\"item",
            "href=\"(https://www\\.sortporn\\.com/videos/\\d+/[^\"]+)\"", "title=\"([^\"]+)\"",
            "src=\"(https://www\\.sortporn\\.com/contents/videos_screenshots/[^\"]+)\"", "class=\"duration\">([0-9:]+)<", null);

    public static final HtmlTube FAPCAT = new HtmlTube(169, "https://www.fapcat.com",
            "https://www.fapcat.com/search/{qp}/", "https://www.fapcat.com/search/{qp}/?from_videos={p}",
            "fapcat\\.com/videos/\\d+/", "<div class=\"item",
            "href=\"(https://www\\.fapcat\\.com/videos/\\d+/[^\"]+)\"", "title=\"([^\"]+)\"",
            "data-src=\"([^\"]+)\"", "class=\"[^\"]*duration[^\"]*\"[^>]*>\\s*(?:<[^>]+>\\s*)*([0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?)", null);

    public static final HtmlTube MATUREKINKYS = new HtmlTube(170, "https://maturekinkys.com",
            "https://maturekinkys.com/search/{qp}/", "https://maturekinkys.com/search/{qp}/?from_videos={p}",
            "maturekinkys\\.com/video/\\d+/", "data-fq-video-card=\"",
            "href=\"(https://maturekinkys\\.com/video/\\d+/[^\"]+)\"", "title=\"([^\"]+)\"",
            "data-original=\"([^\"]+)\"", "class=\"[^\"]*duration[^\"]*\"[^>]*>\\s*(?:<[^>]+>\\s*)*([0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?)", null);

    public static final HtmlTube XOZILLA = new HtmlTube(172, "https://www.xozilla.com",
            "https://www.xozilla.com/search/{qp}/", "https://www.xozilla.com/search/{qp}/?from_videos={p}",
            "xozilla\\.com/videos/\\d+/", "<a href=\"https://www.xozilla.com/videos/",
            "href=\"(https://www\\.xozilla\\.com/videos/\\d+/[^\"]+)\"", "class=\"title\">([^<]+)<",
            "data-original=\"([^\"]+)\"", null, null);

    public static final HtmlTube[] ALL = {HELLPORNO, PORNHAT, BRAVOTUBE, HQPORNER, EROME, PORNGO, GAYPORNTUBE, RULE34VIDEO,
            XXXYMOVIES, ZBPORN, DRTUBER, PORNDOE, THISVID, FREEPORN8, PORNWHITE, SHOOSHTIME, THREEMOVS,
            PERFECTGIRLS, YOUJIZZ, PLAYVIDS, XFIGHTS, FEMDOMVC,
            ANYSEX, DAFREEPORN, JUICYVID, MEGATUBE, SORTPORN,
            FAPCAT, MATUREKINKYS, XOZILLA};

    public static boolean webOnly(String url) {
        HtmlTube t = forUrl(url);
        return t != null && t.web;
    }

    public static HtmlTube forUrl(String url) {
        for (HtmlTube t : ALL) if (t.handles(url)) return t;
        return null;
    }
}
