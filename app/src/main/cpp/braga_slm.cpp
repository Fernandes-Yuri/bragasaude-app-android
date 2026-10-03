#include <jni.h>
#include <llama.h>
#include <memory>
#include <string>
#include <vector>
#include <mutex>
#include <stdexcept>
#include <chrono>
#include <android/log.h>

namespace {
struct Engine {
    llama_model *model = nullptr;
    llama_context *context = nullptr;
    ~Engine() {
        if (context) llama_free(context);
        if (model) llama_model_free(model);
    }
};
void fail(JNIEnv *env, const char *message) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message);
}
std::string bytes(JNIEnv *env, jbyteArray input) {
    std::string result(env->GetArrayLength(input), '\0');
    env->GetByteArrayRegion(input, 0, result.size(), reinterpret_cast<jbyte *>(result.data()));
    return result;
}
jbyteArray array(JNIEnv *env, const std::string &text) {
    auto result = env->NewByteArray(text.size());
    if (result) env->SetByteArrayRegion(result, 0, text.size(), reinterpret_cast<const jbyte *>(text.data()));
    return result;
}
struct Callback {
    JNIEnv *env;
    jobject target;
    jmethodID cancelled;
    bool stopped() { return env->ExceptionCheck() || env->CallBooleanMethod(target, cancelled); }
};
bool abort_decode(void *data) { return static_cast<Callback *>(data)->stopped(); }
// The model handles are protected by the Kotlin mutex. No native global conversation state.
}

extern "C" JNIEXPORT jlong JNICALL
Java_br_com_bragasaude_data_local_slm_BragaNative_load(JNIEnv *env, jobject, jbyteArray path, jint threads) {
    try {
        const auto started = std::chrono::steady_clock::now();
        static std::once_flag initialized;
        std::call_once(initialized, [] { llama_backend_init(); });
        auto engine = std::make_unique<Engine>();
        auto mp = llama_model_default_params();
        mp.n_gpu_layers = 0;
        engine->model = llama_model_load_from_file(bytes(env, path).c_str(), mp);
        if (!engine->model) throw std::runtime_error("Não foi possível carregar o Braga local.");
        auto cp = llama_context_default_params();
        cp.n_ctx = 1024;
        cp.n_batch = 512;
        cp.n_ubatch = 128;
        cp.n_threads = threads;
        cp.n_threads_batch = threads;
        engine->context = llama_init_from_model(engine->model, cp);
        if (!engine->context) throw std::runtime_error("Memória insuficiente para o Braga local.");
        const auto load_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now() - started).count();
        __android_log_print(ANDROID_LOG_INFO, "BragaSlm", "load_ms=%lld threads=%d n_ctx=1024 n_batch=512",
                            static_cast<long long>(load_ms), threads);
        return reinterpret_cast<jlong>(engine.release());
    } catch (const std::exception &e) { fail(env, e.what()); return 0; }
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_br_com_bragasaude_data_local_slm_BragaNative_generate(JNIEnv *env, jobject, jlong handle,
                                                        jbyteArray prompt, jint max_tokens, jobject target) {
    auto *engine = reinterpret_cast<Engine *>(handle);
    if (!engine) { fail(env, "Braga não foi carregado."); return nullptr; }
    Callback callback{env, target, env->GetMethodID(env->GetObjectClass(target), "isCancelled", "()Z")};
    auto progress = env->GetMethodID(env->GetObjectClass(target), "onBytes", "([B)V");
    if (env->ExceptionCheck()) return nullptr;
    llama_set_abort_callback(engine->context, abort_decode, &callback);
    struct Reset {
        llama_context *ctx;
        ~Reset() { llama_set_abort_callback(ctx, nullptr, nullptr); llama_memory_clear(llama_get_memory(ctx), true); }
    } reset{engine->context};
    try {
        const auto started = std::chrono::steady_clock::now();
        llama_memory_clear(llama_get_memory(engine->context), true);
        const auto *vocab = llama_model_get_vocab(engine->model);
        auto text = bytes(env, prompt);
        int count = -llama_tokenize(vocab, text.data(), text.size(), nullptr, 0, false, true);
        if (count <= 0 || count + max_tokens > static_cast<int>(llama_n_ctx(engine->context)))
            throw std::runtime_error("Mensagem longa demais para o Braga local. Inicie uma nova conversa ou reduza o texto.");
        std::vector<llama_token> tokens(count);
        count = llama_tokenize(vocab, text.data(), text.size(), tokens.data(), count, false, true);
        if (count <= 0) throw std::runtime_error("Não foi possível preparar a mensagem.");
        const int batch_size = static_cast<int>(llama_n_batch(engine->context));
        for (int i = 0; i < count; i += batch_size) {
            if (callback.stopped()) return nullptr;
            int size = std::min(batch_size, count - i);
            auto batch = llama_batch_get_one(tokens.data() + i, size);
            if (llama_decode(engine->context, batch) != 0) {
                if (callback.stopped()) return nullptr;
                throw std::runtime_error("Falha na inferência local.");
            }
        }
        const auto prefilled = std::chrono::steady_clock::now();
        std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)> sampler(
            llama_sampler_chain_init(llama_sampler_chain_default_params()), llama_sampler_free);
        llama_sampler_chain_add(sampler.get(), llama_sampler_init_penalties(llama_vocab_n_tokens(vocab), 64, 1.1f, 0, 0));
        llama_sampler_chain_add(sampler.get(), llama_sampler_init_top_k(40));
        llama_sampler_chain_add(sampler.get(), llama_sampler_init_top_p(0.9f, 1));
        llama_sampler_chain_add(sampler.get(), llama_sampler_init_temp(0.1f));
        llama_sampler_chain_add(sampler.get(), llama_sampler_init_dist(42));
        std::string output;
        output.reserve(max_tokens * 8);
        std::string pending;
        pending.reserve(256);
        std::vector<char> piece(256);
        auto last_update = prefilled;
        auto first_token = prefilled;
        int generated = 0;
        int pending_tokens = 0;
        int callbacks = 0;
        auto flush_delta = [&]() {
            if (pending.empty()) return true;
            auto chunk = array(env, pending);
            if (!chunk) return false;
            env->CallVoidMethod(target, progress, chunk);
            env->DeleteLocalRef(chunk);
            pending.clear();
            pending_tokens = 0;
            ++callbacks;
            last_update = std::chrono::steady_clock::now();
            return !env->ExceptionCheck();
        };
        for (int i = 0; i < max_tokens; ++i) {
            if (callback.stopped()) return nullptr;
            auto token = llama_sampler_sample(sampler.get(), engine->context, -1);
            if (llama_vocab_is_eog(vocab, token)) break;
            int size = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false);
            if (size < 0) {
                piece.resize(-size);
                size = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false);
            }
            if (size > 0) {
                output.append(piece.data(), size);
                pending.append(piece.data(), size);
            }
            ++generated;
            ++pending_tokens;
            const auto now = std::chrono::steady_clock::now();
            if (generated == 1) first_token = now;
            if (generated == 1 || pending_tokens >= 4 || now - last_update >= std::chrono::milliseconds(80)) {
                if (!flush_delta()) return nullptr;
            }
            // O último token não precisa de decode: não haverá uma próxima amostragem.
            if (i + 1 == max_tokens) break;
            auto batch = llama_batch_get_one(&token, 1);
            if (llama_decode(engine->context, batch) != 0) {
                if (callback.stopped()) return nullptr;
                throw std::runtime_error("Falha na geração da resposta local.");
            }
        }
        if (callback.stopped() || !flush_delta()) return nullptr;
        const auto finished = std::chrono::steady_clock::now();
        const auto total_ms = std::chrono::duration_cast<std::chrono::milliseconds>(finished - started).count();
        const auto prefill_ms = std::chrono::duration_cast<std::chrono::milliseconds>(prefilled - started).count();
        const auto ttft_ms = std::chrono::duration_cast<std::chrono::milliseconds>(first_token - started).count();
        __android_log_print(ANDROID_LOG_INFO, "BragaSlm",
                            "prompt_tokens=%d generated_tokens=%d callbacks=%d prefill_ms=%lld ttft_ms=%lld total_ms=%lld",
                            count, generated, callbacks, static_cast<long long>(prefill_ms),
                            static_cast<long long>(ttft_ms), static_cast<long long>(total_ms));
        return array(env, output);
    } catch (const std::exception &e) { fail(env, e.what()); return nullptr; }
}

extern "C" JNIEXPORT void JNICALL
Java_br_com_bragasaude_data_local_slm_BragaNative_release(JNIEnv *, jobject, jlong handle) {
    delete reinterpret_cast<Engine *>(handle);
}
