#include "anitomy_bridge.h"

#include <new>
#include <string_view>
#include <utility>
#include <vector>

#include <anitomy_standard_library_compat.hpp>
#include <anitomy/detail/parser.hpp>
#include <anitomy/detail/tokenizer.hpp>
#include <anitomy/element.hpp>
#include <anitomy/options.hpp>

#if defined(__linux__)
// Modern libstdc++ uses this glibc optimization marker. Kotlin/Native's intentionally old Linux
// sysroot predates it, so define the conservative multi-threaded value in the final executable.
extern "C" {
char __libc_single_threaded = 0;
}
#endif

struct anitomy_result {
    std::vector<anitomy::Element> elements;
};

namespace {

anitomy::Options make_options(const uint32_t mask) noexcept {
    return anitomy::Options{
        .parse_episode = (mask & ANITOMY_OPTION_PARSE_EPISODE) != 0,
        .parse_episode_title = (mask & ANITOMY_OPTION_PARSE_EPISODE_TITLE) != 0,
        .parse_file_checksum = (mask & ANITOMY_OPTION_PARSE_FILE_CHECKSUM) != 0,
        .parse_file_extension = (mask & ANITOMY_OPTION_PARSE_FILE_EXTENSION) != 0,
        .parse_part = (mask & ANITOMY_OPTION_PARSE_PART) != 0,
        .parse_release_group = (mask & ANITOMY_OPTION_PARSE_RELEASE_GROUP) != 0,
        .parse_season = (mask & ANITOMY_OPTION_PARSE_SEASON) != 0,
        .parse_title = (mask & ANITOMY_OPTION_PARSE_TITLE) != 0,
        .parse_video_resolution = (mask & ANITOMY_OPTION_PARSE_VIDEO_RESOLUTION) != 0,
        .parse_year = (mask & ANITOMY_OPTION_PARSE_YEAR) != 0,
    };
}

anitomy_element_kind to_bridge_kind(const anitomy::ElementKind kind) noexcept {
    switch (kind) {
        case anitomy::ElementKind::AudioTerm:
            return ANITOMY_ELEMENT_AUDIO_TERM;
        case anitomy::ElementKind::Device:
            return ANITOMY_ELEMENT_DEVICE;
        case anitomy::ElementKind::Episode:
            return ANITOMY_ELEMENT_EPISODE;
        case anitomy::ElementKind::EpisodeTitle:
            return ANITOMY_ELEMENT_EPISODE_TITLE;
        case anitomy::ElementKind::FileChecksum:
            return ANITOMY_ELEMENT_FILE_CHECKSUM;
        case anitomy::ElementKind::FileExtension:
            return ANITOMY_ELEMENT_FILE_EXTENSION;
        case anitomy::ElementKind::Language:
            return ANITOMY_ELEMENT_LANGUAGE;
        case anitomy::ElementKind::Other:
            return ANITOMY_ELEMENT_OTHER;
        case anitomy::ElementKind::Part:
            return ANITOMY_ELEMENT_PART;
        case anitomy::ElementKind::ReleaseGroup:
            return ANITOMY_ELEMENT_RELEASE_GROUP;
        case anitomy::ElementKind::ReleaseInformation:
            return ANITOMY_ELEMENT_RELEASE_INFORMATION;
        case anitomy::ElementKind::ReleaseVersion:
            return ANITOMY_ELEMENT_RELEASE_VERSION;
        case anitomy::ElementKind::Season:
            return ANITOMY_ELEMENT_SEASON;
        case anitomy::ElementKind::Source:
            return ANITOMY_ELEMENT_SOURCE;
        case anitomy::ElementKind::Subtitles:
            return ANITOMY_ELEMENT_SUBTITLES;
        case anitomy::ElementKind::Title:
            return ANITOMY_ELEMENT_TITLE;
        case anitomy::ElementKind::Type:
            return ANITOMY_ELEMENT_TYPE;
        case anitomy::ElementKind::VideoResolution:
            return ANITOMY_ELEMENT_VIDEO_RESOLUTION;
        case anitomy::ElementKind::VideoTerm:
            return ANITOMY_ELEMENT_VIDEO_TERM;
        case anitomy::ElementKind::Volume:
            return ANITOMY_ELEMENT_VOLUME;
        case anitomy::ElementKind::Year:
            return ANITOMY_ELEMENT_YEAR;
    }
    return ANITOMY_ELEMENT_OTHER;
}

const anitomy::Element* element_at(const anitomy_result* result, const size_t index) noexcept {
    if (result == nullptr || index >= result->elements.size()) {
        return nullptr;
    }
    return &result->elements[index];
}

std::vector<anitomy::Element> parse(
    const std::string_view input,
    const anitomy::Options options
) noexcept {
    anitomy::detail::Tokenizer tokenizer{input};
    tokenizer.tokenize(options);

    anitomy::detail::Parser parser{tokenizer.tokens()};
    parser.parse(options);
    return parser.elements();
}

}  // namespace

extern "C" anitomy_result* anitomy_parse_utf8(
    const char* input,
    const size_t input_length,
    const uint32_t options
) {
    if (input == nullptr && input_length != 0) {
        return nullptr;
    }

    const std::string_view input_view{input == nullptr ? "" : input, input_length};
    auto elements = parse(input_view, make_options(options));
    return new (std::nothrow) anitomy_result{.elements = std::move(elements)};
}

extern "C" size_t anitomy_result_count(const anitomy_result* result) {
    return result == nullptr ? 0 : result->elements.size();
}

extern "C" anitomy_element_kind anitomy_result_kind(
    const anitomy_result* result,
    const size_t index
) {
    const auto* element = element_at(result, index);
    return element == nullptr ? ANITOMY_ELEMENT_OTHER : to_bridge_kind(element->kind);
}

extern "C" const char* anitomy_result_value(
    const anitomy_result* result,
    const size_t index,
    size_t* value_length
) {
    const auto* element = element_at(result, index);
    if (element == nullptr) {
        if (value_length != nullptr) {
            *value_length = 0;
        }
        return nullptr;
    }

    if (value_length != nullptr) {
        *value_length = element->value.size();
    }
    return element->value.data();
}

extern "C" uint64_t anitomy_result_position(
    const anitomy_result* result,
    const size_t index
) {
    const auto* element = element_at(result, index);
    return element == nullptr ? 0 : static_cast<uint64_t>(element->position);
}

extern "C" void anitomy_result_destroy(anitomy_result* result) {
    delete result;
}
