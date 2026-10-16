#include "whisper.h"

#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

static bool read_exact(void * dst, size_t n) {
    char * p = static_cast<char *>(dst);
    while (n > 0) {
        size_t got = fread(p, 1, n, stdin);
        if (got == 0) return false;
        p += got;
        n -= got;
    }
    return true;
}

static void quiet(ggml_log_level, const char *, void *) {
}

int main(int argc, char ** argv) {
    if (argc < 2) {
        fprintf(stderr, "usage: %s model.bin [threads] [language]\n", argv[0]);
        return 2;
    }
    const char * model = argv[1];
    int threads = argc > 2 ? atoi(argv[2]) : 4;
    std::string language = argc > 3 ? argv[3] : "auto";

    whisper_log_set(quiet, nullptr);
    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    whisper_context * ctx = whisper_init_from_file_with_params(model, cparams);
    if (ctx == nullptr) {
        fprintf(stdout, "E\tmodel\n");
        fflush(stdout);
        return 1;
    }
    fprintf(stdout, "R\n");
    fflush(stdout);

    std::vector<int16_t> pcm;
    std::vector<float> samples;
    while (true) {
        int64_t start_ms = 0;
        int32_t count = 0;
        if (!read_exact(&start_ms, sizeof(start_ms)) || !read_exact(&count, sizeof(count))) break;
        if (count <= 0 || count > 16000 * 60) break;
        pcm.resize(count);
        if (!read_exact(pcm.data(), count * sizeof(int16_t))) break;
        samples.resize(count);
        for (int i = 0; i < count; i++) samples[i] = pcm[i] / 32768.0f;

        whisper_full_params p = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
        p.n_threads = threads;
        p.language = language.c_str();
        p.detect_language = false;
        p.translate = false;
        p.no_context = true;
        p.single_segment = false;
        p.print_progress = false;
        p.print_realtime = false;
        p.print_special = false;
        p.print_timestamps = false;
        p.suppress_blank = true;
        p.suppress_nst = true;
        p.no_speech_thold = 0.6f;
        p.audio_ctx = std::min(1500, (int) (count * 50LL / 16000) + 64);

        if (whisper_full(ctx, p, samples.data(), (int) samples.size()) != 0) {
            fprintf(stdout, "E\t%lld\n", (long long) start_ms);
            fflush(stdout);
            continue;
        }
        const char * lang = whisper_lang_str(whisper_full_lang_id(ctx));
        int n = whisper_full_n_segments(ctx);
        for (int i = 0; i < n; i++) {
            if (whisper_full_get_segment_no_speech_prob(ctx, i) > 0.6f) continue;
            const char * text = whisper_full_get_segment_text(ctx, i);
            if (text == nullptr || text[0] == '\0') continue;
            long long t0 = start_ms + whisper_full_get_segment_t0(ctx, i) * 10;
            long long t1 = start_ms + whisper_full_get_segment_t1(ctx, i) * 10;
            std::string line(text);
            for (char & c : line) if (c == '\n' || c == '\t' || c == '\r') c = ' ';
            fprintf(stdout, "S\t%lld\t%lld\t%s\t%s\n", t0, t1, lang ? lang : "", line.c_str());
        }
        fprintf(stdout, "D\t%lld\n", (long long) start_ms);
        fflush(stdout);
    }
    whisper_free(ctx);
    return 0;
}
