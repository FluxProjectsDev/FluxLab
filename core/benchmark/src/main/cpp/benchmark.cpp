#include <jni.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <limits>
#include <numeric>
#include <thread>
#include <vector>

namespace {
std::atomic<bool> cancelled{false};
volatile uint64_t checksum_sink = 0;

uint64_t next_random(uint64_t &state) {
    state ^= state << 13U;
    state ^= state >> 7U;
    state ^= state << 17U;
    return state;
}

int64_t now_ns() {
    return std::chrono::duration_cast<std::chrono::nanoseconds>(
               std::chrono::steady_clock::now().time_since_epoch())
        .count();
}

struct Result {
    double value = -1.0;
    uint64_t checksum = 0;
    int64_t duration_ns = 0;
    int threads = 1;
};

Result cpu_integer(uint64_t iterations, uint64_t seed) {
    uint64_t state = seed | 1U;
    uint64_t accumulator = 0x9E3779B97F4A7C15ULL;
    const int64_t start = now_ns();
    for (uint64_t i = 0; i < iterations; ++i) {
        const uint64_t value = next_random(state);
        accumulator ^= (value + i) * 0xD6E8FEB86659FD93ULL;
        accumulator = (accumulator << 17U) | (accumulator >> 47U);
        if ((i & 0x3FFFU) == 0U && cancelled.load(std::memory_order_relaxed)) return {};
    }
    const int64_t duration = std::max<int64_t>(1, now_ns() - start);
    checksum_sink = checksum_sink ^ accumulator;
    return {static_cast<double>(iterations) * 1e9 / static_cast<double>(duration), accumulator, duration, 1};
}

Result cpu_float(uint64_t iterations, uint64_t seed) {
    double x = 0.25 + static_cast<double>(seed % 97U) / 101.0;
    double sum = 0.0;
    const int64_t start = now_ns();
    for (uint64_t i = 0; i < iterations; ++i) {
        x = std::fma(x, 1.0000001192092896, 0.0000009536743164);
        x = x > 8.0 ? x * 0.125 : x;
        sum += std::sqrt(x + static_cast<double>(i & 31U)) * 0.03125;
        if ((i & 0x3FFFU) == 0U && cancelled.load(std::memory_order_relaxed)) return {};
    }
    const int64_t duration = std::max<int64_t>(1, now_ns() - start);
    uint64_t checksum = 0;
    std::memcpy(&checksum, &sum, sizeof(checksum));
    checksum_sink = checksum_sink ^ checksum;
    return {static_cast<double>(iterations * 5U) * 1e9 / static_cast<double>(duration), checksum, duration, 1};
}

Result cpu_multi(uint64_t iterations, uint64_t seed, int requested_threads) {
    const int threads = std::clamp(requested_threads, 2, 8);
    std::vector<std::thread> workers;
    std::vector<uint64_t> sums(static_cast<size_t>(threads), 0);
    const int64_t start = now_ns();
    for (int thread = 0; thread < threads; ++thread) {
        workers.emplace_back([=, &sums] {
            uint64_t state = seed + static_cast<uint64_t>(thread) * 0x9E3779B97F4A7C15ULL;
            uint64_t sum = 0;
            for (uint64_t i = 0; i < iterations; ++i) {
                sum += next_random(state) ^ (i * 0xD6E8FEB86659FD93ULL);
                if ((i & 0x3FFFU) == 0U && cancelled.load(std::memory_order_relaxed)) break;
            }
            sums[static_cast<size_t>(thread)] = sum;
        });
    }
    for (auto &worker : workers) worker.join();
    if (cancelled.load(std::memory_order_relaxed)) return {};
    const int64_t duration = std::max<int64_t>(1, now_ns() - start);
    const uint64_t checksum = std::accumulate(sums.begin(), sums.end(), uint64_t{0});
    checksum_sink = checksum_sink ^ checksum;
    const double operations = static_cast<double>(iterations) * static_cast<double>(threads);
    return {operations * 1e9 / static_cast<double>(duration), checksum, duration, threads};
}

Result memory_bandwidth(size_t bytes, int repetitions, uint64_t seed, bool fill) {
    bytes = std::clamp<size_t>(bytes, 1U << 20U, 32U << 20U);
    std::vector<uint8_t> source(bytes);
    std::vector<uint8_t> destination(bytes);
    uint64_t random = seed;
    for (size_t i = 0; i < bytes; ++i) source[i] = static_cast<uint8_t>(next_random(random));
    const int64_t start = now_ns();
    for (int repetition = 0; repetition < repetitions; ++repetition) {
        if (fill) {
            std::memset(destination.data(), static_cast<int>((seed + repetition) & 0xFFU), bytes);
        } else {
            std::memcpy(destination.data(), source.data(), bytes);
        }
        if (cancelled.load(std::memory_order_relaxed)) return {};
    }
    const int64_t duration = std::max<int64_t>(1, now_ns() - start);
    uint64_t checksum = 0;
    for (size_t i = 0; i < bytes; i += 4096U) checksum = checksum * 131U + destination[i];
    checksum_sink = checksum_sink ^ checksum;
    const double mib = static_cast<double>(bytes) * repetitions / (1024.0 * 1024.0);
    return {mib * 1e9 / static_cast<double>(duration), checksum, duration, 1};
}

Result memory_latency(size_t bytes, uint64_t accesses, uint64_t seed) {
    const size_t count = std::clamp<size_t>(bytes / sizeof(uint32_t), 1U << 18U, 8U << 20U);
    std::vector<uint32_t> order(count);
    std::iota(order.begin(), order.end(), 0U);
    uint64_t random = seed;
    for (size_t i = count - 1; i > 0; --i) {
        const size_t other = static_cast<size_t>(next_random(random) % (i + 1));
        std::swap(order[i], order[other]);
    }
    std::vector<uint32_t> next(count);
    for (size_t i = 0; i < count; ++i) next[order[i]] = order[(i + 1) % count];
    uint32_t cursor = 0;
    const int64_t start = now_ns();
    for (uint64_t i = 0; i < accesses; ++i) {
        cursor = next[cursor];
        if ((i & 0x3FFFU) == 0U && cancelled.load(std::memory_order_relaxed)) return {};
    }
    const int64_t duration = std::max<int64_t>(1, now_ns() - start);
    checksum_sink = checksum_sink ^ cursor;
    return {static_cast<double>(duration) / static_cast<double>(accesses), cursor, duration, 1};
}
} // namespace

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_febricahyaa_fluxlab_benchmark_NativeBridge_run(
    JNIEnv *env, jobject, jint kind, jlong size, jlong iterations, jlong seed, jint threads) {
    cancelled.store(false, std::memory_order_relaxed);
    Result result;
    switch (kind) {
        case 0: result = cpu_integer(static_cast<uint64_t>(iterations), static_cast<uint64_t>(seed)); break;
        case 1: result = cpu_float(static_cast<uint64_t>(iterations), static_cast<uint64_t>(seed)); break;
        case 2: result = cpu_multi(static_cast<uint64_t>(iterations), static_cast<uint64_t>(seed), threads); break;
        case 3: result = memory_bandwidth(static_cast<size_t>(size), static_cast<int>(iterations), static_cast<uint64_t>(seed), false); break;
        case 4: result = memory_bandwidth(static_cast<size_t>(size), static_cast<int>(iterations), static_cast<uint64_t>(seed), true); break;
        case 5: result = memory_latency(static_cast<size_t>(size), static_cast<uint64_t>(iterations), static_cast<uint64_t>(seed)); break;
        default: break;
    }
    const jdouble values[] = {
        result.value,
        static_cast<double>(result.checksum),
        static_cast<double>(result.duration_ns),
        static_cast<double>(result.threads),
    };
    jdoubleArray output = env->NewDoubleArray(4);
    if (output != nullptr) env->SetDoubleArrayRegion(output, 0, 4, values);
    return output;
}

extern "C" JNIEXPORT void JNICALL
Java_com_febricahyaa_fluxlab_benchmark_NativeBridge_cancel(JNIEnv *, jobject) {
    cancelled.store(true, std::memory_order_relaxed);
}
