# Performance

One row per release. Search quality comes from `test/RankBench.java`, which ranks the same saved results for 10 fixed queries with every version. A result is junk when the query is not in its title or channel, when it is in another script, or when it has under 1,000 views. Start times are medians of 3 runs over Tor on a phone.

| | v0.1.0 | v0.2.1 | v0.3.0 | v0.3.1 | v0.3.2 | v0.3.3 |
|---|---|---|---|---|---|---|
| Sources | 56 built in | 68 built in | 70 from repositories | 96 from repositories | 103 from repositories | 103 from repositories |
| Junk in top 10 (10 queries) | 37 | 37 | 8 | 8 | 8 | 8 |
| First YouTube result, average position | 1.5 | 1.5 | 1.2 | 1.2 | 1.2 | 1.2 |
| Tap to first frame, YouTube | 5.9 s | 5.9 s | 0.3 s (centre video), 2.6 s (others) | — | — | — |
| Requests when a video opens | ~11 + 12–20 thumbnails | ~11 + 12–20 thumbnails | 3 + 6 thumbnails | — | — | — |
| APK size | 12.1 MB | 29.6 MB | 29.7 MB | 28.3 MB | 28.3 MB | 28.1 MB |

## Reproduce

```sh
CP="build/classes:$(ls libs/*.jar | tr '\n' ':')tools/android33.jar"
javac -cp "$CP" -d /tmp/rb test/RankBench.java
java -cp "/tmp/rb:$CP" RankBench
```

The saved results come from `test/MixProbe.java`.
