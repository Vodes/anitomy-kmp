#include "anitomy_bridge.h"

#include <cstdio>
#include <cstdlib>
#include <string_view>

#define CHECK(condition)                                                        \
    do {                                                                        \
        if (!(condition)) {                                                     \
            std::fprintf(                                                       \
                stderr, "%s:%d: check failed: %s\n", __FILE__, __LINE__, #condition \
            );                                                                  \
            return EXIT_FAILURE;                                                \
        }                                                                       \
    } while (false)

namespace {

std::string_view value_at(const anitomy_result* result, const size_t index) {
    size_t length = 0;
    const char* value = anitomy_result_value(result, index, &length);
    return {value, length};
}

bool has_element(
    const anitomy_result* result,
    const anitomy_element_kind kind,
    const std::string_view value
) {
    for (size_t index = 0; index < anitomy_result_count(result); ++index) {
        if (anitomy_result_kind(result, index) == kind && value_at(result, index) == value) {
            return true;
        }
    }
    return false;
}

}  // namespace

int main() {
    constexpr std::string_view input{"[Vodes] Fumetsu no Anata e - S01E15.mkv"};
    anitomy_result* result = anitomy_parse_utf8(input.data(), input.size(), ANITOMY_OPTIONS_ALL);

    CHECK(result != nullptr);
    CHECK(anitomy_result_count(result) == 5);
    CHECK(anitomy_result_kind(result, 0) == ANITOMY_ELEMENT_RELEASE_GROUP);
    CHECK(value_at(result, 0) == "Vodes");
    CHECK(anitomy_result_kind(result, 1) == ANITOMY_ELEMENT_TITLE);
    CHECK(value_at(result, 1) == "Fumetsu no Anata e");
    CHECK(anitomy_result_kind(result, 2) == ANITOMY_ELEMENT_SEASON);
    CHECK(value_at(result, 2) == "01");
    CHECK(anitomy_result_kind(result, 3) == ANITOMY_ELEMENT_EPISODE);
    CHECK(value_at(result, 3) == "15");
    CHECK(anitomy_result_kind(result, 4) == ANITOMY_ELEMENT_FILE_EXTENSION);
    CHECK(value_at(result, 4) == "mkv");

    anitomy_result_destroy(result);

    constexpr std::string_view fractional{
        "[EroGaKi-Team]_Nurse_Witch_Komugi-chan_Magikarte_02.5_[902BB314].mkv"
    };
    result =
        anitomy_parse_utf8(fractional.data(), fractional.size(), ANITOMY_OPTIONS_ALL);
    CHECK(result != nullptr);
    CHECK(has_element(result, ANITOMY_ELEMENT_EPISODE, "02.5"));
    anitomy_result_destroy(result);

    constexpr std::string_view adjacent{"Example Show 2nd Season (2024) - 03.mkv"};
    result = anitomy_parse_utf8(adjacent.data(), adjacent.size(), ANITOMY_OPTIONS_ALL);
    CHECK(result != nullptr);
    CHECK(has_element(result, ANITOMY_ELEMENT_SEASON, "2"));
    CHECK(has_element(result, ANITOMY_ELEMENT_YEAR, "2024"));
    anitomy_result_destroy(result);

    constexpr std::string_view partial{"Example Show - 111C.mkv"};
    result = anitomy_parse_utf8(partial.data(), partial.size(), ANITOMY_OPTIONS_ALL);
    CHECK(result != nullptr);
    CHECK(has_element(result, ANITOMY_ELEMENT_EPISODE, "111C"));
    anitomy_result_destroy(result);

    result = anitomy_parse_utf8("", 0, ANITOMY_OPTIONS_ALL);
    CHECK(result != nullptr);
    CHECK(anitomy_result_count(result) == 0);
    anitomy_result_destroy(result);

    return EXIT_SUCCESS;
}
