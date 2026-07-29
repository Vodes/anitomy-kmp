#include "anitomy_bridge.h"

#include <cassert>
#include <string_view>

namespace {

std::string_view value_at(const anitomy_result* result, const size_t index) {
    size_t length = 0;
    const char* value = anitomy_result_value(result, index, &length);
    return {value, length};
}

}  // namespace

int main() {
    constexpr std::string_view input{"[Vodes] Fumetsu no Anata e - S01E15.mkv"};
    anitomy_result* result = anitomy_parse_utf8(input.data(), input.size(), ANITOMY_OPTIONS_ALL);

    assert(result != nullptr);
    assert(anitomy_result_count(result) == 5);
    assert(anitomy_result_kind(result, 0) == ANITOMY_ELEMENT_RELEASE_GROUP);
    assert(value_at(result, 0) == "Vodes");
    assert(anitomy_result_kind(result, 1) == ANITOMY_ELEMENT_TITLE);
    assert(value_at(result, 1) == "Fumetsu no Anata e");
    assert(anitomy_result_kind(result, 2) == ANITOMY_ELEMENT_SEASON);
    assert(value_at(result, 2) == "01");
    assert(anitomy_result_kind(result, 3) == ANITOMY_ELEMENT_EPISODE);
    assert(value_at(result, 3) == "15");
    assert(anitomy_result_kind(result, 4) == ANITOMY_ELEMENT_FILE_EXTENSION);
    assert(value_at(result, 4) == "mkv");

    anitomy_result_destroy(result);

    result = anitomy_parse_utf8("", 0, ANITOMY_OPTIONS_ALL);
    assert(result != nullptr);
    assert(anitomy_result_count(result) == 0);
    anitomy_result_destroy(result);

    return 0;
}
