#pragma once
#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <stdexcept>
#include <vector>

namespace velvet_long_context {

/**
 * Engine-independent CLIP tokenizer output packer.
 *
 * Uses the same 75 content + BOS/EOS = 77 tokens per CLIP pass that
 * AUTOMATIC1111's SD1.5 long-prompt path uses. A native tokenizer must
 * supply exact BPE token IDs; words and regex splits are NOT adequate.
 * This ONLY prepares ID batches. Native text encoding, concatenate-hidden-
 * states, MNN UNet dynamic attention and heat/RAM checks are still required.
 */
constexpr std::int32_t kBOS = 49406;
constexpr std::int32_t kEOS = 49407;
constexpr std::size_t kClipContent = 75;
constexpr std::size_t kClipSequence = 77;

using IDs = std::vector<std::int32_t>;
using Chunks = std::vector<IDs>;

inline std::size_t countChunks(std::size_t content) {
    return std::max<std::size_t>(1, (content + kClipContent - 1) / kClipContent);
}

inline Chunks encodeChunks(const IDs& bpe, std::size_t maxChunks = 4) {
    if (maxChunks < 1 || maxChunks > 32)
        throw std::invalid_argument("maxChunks must be 1 to 32");
    const std::size_t need = countChunks(bpe.size());
    if (need > maxChunks)
        throw std::length_error("CLIP context would exceed mobile chunk budget");
    Chunks result;
    result.reserve(need);
    for (std::size_t c = 0; c < need; ++c) {
        IDs seq(kClipSequence, kEOS);
        seq[0] = kBOS;
        const std::size_t begin = c * kClipContent;
        const std::size_t stop = std::min(bpe.size(), begin + kClipContent);
        for (std::size_t i = begin; i < stop; ++i)
            seq[1 + i - begin] = bpe[i];
        result.push_back(std::move(seq));
    }
    return result;
}

struct PairedChunks {
    Chunks positive;
    Chunks negative;
    std::size_t rows;
};

/** Match positive and negative context lengths before CFG batching. */
inline PairedChunks pair(const IDs& positive, const IDs& negative,
                         std::size_t maxChunks = 4) {
    Chunks p = encodeChunks(positive, maxChunks);
    Chunks n = encodeChunks(negative, maxChunks);
    const std::size_t count = std::max(p.size(), n.size());
    const auto padded = encodeChunks({}, 1).front();
    p.resize(count, padded);
    n.resize(count, padded);
    return {std::move(p), std::move(n), count * kClipSequence};
}
}
