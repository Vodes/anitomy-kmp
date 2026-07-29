#ifndef ANITOMY_KMP_BRIDGE_H
#define ANITOMY_KMP_BRIDGE_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct anitomy_result anitomy_result;

typedef enum anitomy_element_kind {
    ANITOMY_ELEMENT_AUDIO_TERM = 0,
    ANITOMY_ELEMENT_DEVICE = 1,
    ANITOMY_ELEMENT_EPISODE = 2,
    ANITOMY_ELEMENT_EPISODE_TITLE = 3,
    ANITOMY_ELEMENT_FILE_CHECKSUM = 4,
    ANITOMY_ELEMENT_FILE_EXTENSION = 5,
    ANITOMY_ELEMENT_LANGUAGE = 6,
    ANITOMY_ELEMENT_OTHER = 7,
    ANITOMY_ELEMENT_PART = 8,
    ANITOMY_ELEMENT_RELEASE_GROUP = 9,
    ANITOMY_ELEMENT_RELEASE_INFORMATION = 10,
    ANITOMY_ELEMENT_RELEASE_VERSION = 11,
    ANITOMY_ELEMENT_SEASON = 12,
    ANITOMY_ELEMENT_SOURCE = 13,
    ANITOMY_ELEMENT_SUBTITLES = 14,
    ANITOMY_ELEMENT_TITLE = 15,
    ANITOMY_ELEMENT_TYPE = 16,
    ANITOMY_ELEMENT_VIDEO_RESOLUTION = 17,
    ANITOMY_ELEMENT_VIDEO_TERM = 18,
    ANITOMY_ELEMENT_VOLUME = 19,
    ANITOMY_ELEMENT_YEAR = 20
} anitomy_element_kind;

typedef enum anitomy_option {
    ANITOMY_OPTION_PARSE_EPISODE = UINT32_C(1) << 0,
    ANITOMY_OPTION_PARSE_EPISODE_TITLE = UINT32_C(1) << 1,
    ANITOMY_OPTION_PARSE_FILE_CHECKSUM = UINT32_C(1) << 2,
    ANITOMY_OPTION_PARSE_FILE_EXTENSION = UINT32_C(1) << 3,
    ANITOMY_OPTION_PARSE_PART = UINT32_C(1) << 4,
    ANITOMY_OPTION_PARSE_RELEASE_GROUP = UINT32_C(1) << 5,
    ANITOMY_OPTION_PARSE_SEASON = UINT32_C(1) << 6,
    ANITOMY_OPTION_PARSE_TITLE = UINT32_C(1) << 7,
    ANITOMY_OPTION_PARSE_VIDEO_RESOLUTION = UINT32_C(1) << 8,
    ANITOMY_OPTION_PARSE_YEAR = UINT32_C(1) << 9,
    ANITOMY_OPTIONS_ALL = (UINT32_C(1) << 10) - 1
} anitomy_option;

anitomy_result* anitomy_parse_utf8(const char* input, size_t input_length, uint32_t options);

size_t anitomy_result_count(const anitomy_result* result);

anitomy_element_kind anitomy_result_kind(const anitomy_result* result, size_t index);

const char* anitomy_result_value(
    const anitomy_result* result,
    size_t index,
    size_t* value_length
);

uint64_t anitomy_result_position(const anitomy_result* result, size_t index);

void anitomy_result_destroy(anitomy_result* result);

#ifdef __cplusplus
}
#endif

#endif
