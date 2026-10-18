# Performance

One row per measurement, one column per release. Search quality comes from `test/RankBench.java`, which ranks the same saved results for 10 fixed queries with every version. A result is junk when the query is not in its title or channel, when it is in another script, or when it has under 1,000 views. Times are measured on a phone over Tor. A cell is empty when that release was not measured.

| | v0.1.0 | v0.2.1 | v0.3.0 | v0.3.1 | v0.3.2 | v0.3.3 | v0.3.4 | v0.3.5 |
|---|---|---|---|---|---|---|---|---|
| Sources | 56 built in | 68 built in | 70 from repositories | 96 from repositories | 103 from repositories | 103 from repositories | 103 from repositories | 103 from repositories |
| Junk in top 10 (10 queries) | 37 | 37 | 8 | 8 | 8 | 8 | 8 | 8 |
| First YouTube result, average position | 1.5 | 1.5 | 1.2 | 1.2 | 1.2 | 1.2 | 1.2 | 1.2 |
| Tap to first frame, YouTube | 5.9 s | 5.9 s | 0.3 s (centre video), 2.6 s (others) | - | - | - | - | - |
| Requests when a video opens | ~11 + 12-20 thumbnails | ~11 + 12-20 thumbnails | 3 + 6 thumbnails | - | - | - | - | - |
| First 8 YouTube thumbnails, download time | - | - | - | - | - | 5.1 s | 2.4 s | - |
| Opening a video from a repository source, median / slowest 10% | - | - | - | - | - | 2.38 s / 6.13 s | 2.07 s / 4.55 s | - |
| Vertical YouTube video that has 1080p, quality picked | - | - | - | - | - | 480p-608p | 1080p | - |
| Error for an Instagram video that needs a login | - | - | - | - | - | 3-20 s | 1-3 s | - |
| Script sources, CPU time on the first search (22 scripts) | - | - | - | - | - | 1005 ms | 38 ms | - |
| Right YouTube search sent before typing ends (6 queries) | - | - | - | - | - | none | 5 of 6 | - |
| YouTube search 1 s after Tor is ready | - | - | - | - | - | - | 14.7-17.9 s | 3.4-4.0 s |
| APK size | 12.1 MB | 29.6 MB | 29.7 MB | 28.3 MB | 28.3 MB | 28.1 MB | 28.1 MB | 26.9 MB |

## Reproduce

```sh
CP="build/classes:$(ls libs/*.jar | tr '\n' ':')tools/android33.jar"
javac -cp "$CP" -d /tmp/rb test/RankBench.java
java -cp "/tmp/rb:$CP" RankBench
```

The saved results come from `test/MixProbe.java`. The rows added in v0.3.4 come from these probes, run the same way: `test/ThumbAB.java` (thumbnails, 3 runs of 8), `test/HedgeAB.java` (24 videos, each opened both ways in alternating order), `test/VqProbe.java` (quality), `test/InstaProbe.java` (Instagram), `test/RhinoCost.java` (scripts) and `test/TypeSim.java` (typing at 180 ms per letter). `test/FewProbe.java` saves the search results the first three read. The Tor start row in v0.3.5 comes from `test/StormProbe.java`, which warms the same number of circuits as the app at start (48 in v0.3.4, 12 in v0.3.5) and then runs one YouTube search.
