# Performance by version

Each release adds a row. Search quality is measured with `test/RankBench.java`: every version's ranker gets the same saved result lists for 10 queries (`reddit`, `minecraft`, `barış manço`, `how to cook rice`, `deprem`, `spotify`, `python tutorial`, `phandeon`, `rick astley never gonna give you up`, `lofi hip hop radio beats to relax study`) with the same source weights. A result counts as junk when neither its title nor its channel contains a query word, when it is in another script than the query, or when it has fewer than 1,000 views. Start times were measured over Tor on the phone (median of 3 runs on fresh circuits, 4 YouTube videos).

| | v0.1.0 | v0.2.1 | v0.3.0 |
|---|---|---|---|
| Sources | 56 built in | 68 built in | 70 in two repositories, none built in |
| Junk results in top 10, 10 queries | 37 | 37 | 8 |
| Average position of the first YouTube result | 1.5 | 1.5 | 1.2 |
| Tap to first-frame bytes, YouTube | 5.9 s | 5.9 s | 0.3 s for the video in the middle of the screen, 2.6 s for other visible videos |
| Requests when a YouTube video opens | ~11 + 12–20 thumbnails | ~11 + 12–20 thumbnails | 3 + 6 thumbnails |
| APK size | 12.1 MB | 29.6 MB | 29.7 MB |

## v0.3.0 changes behind the numbers

- Popularity counts real view numbers across all sources instead of each source's own ranking, results in another script than the query sink, a channel whose name matches the query exactly gets a strong boost, and one channel takes at most two of the top places.
- Late sources re-rank the list until you scroll.
- While you browse, Orvid resolves every fully visible video and fetches the first frame of the one in the middle of the screen and its two neighbours; long press and the next related video get their first seconds.
- Opening a video asks for one page of related videos. Searches on other sites run only when that page has fewer than six results, and chapter frames load when the description opens.

## Reproduce

```sh
javac -cp "build/classes:$(ls libs/*.jar | tr '\n' ':')tools/android33.jar" -d /tmp/rb test/RankBench.java
java -cp "/tmp/rb:build/classes:$(ls libs/*.jar | tr '\n' ':')tools/android33.jar" RankBench
```

The saved result lists come from `test/MixProbe.java`, which searches every source over Tor and stores them.
