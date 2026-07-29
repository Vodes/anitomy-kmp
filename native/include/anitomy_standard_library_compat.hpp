#pragma once

#include <algorithm>
#include <functional>
#include <ranges>
#include <utility>
#include <version>

// Anitomy v2 uses the C++23 std::ranges::starts_with algorithm. GCC 15 supports
// the language level needed by Anitomy but its libstdc++ does not provide that
// one algorithm yet. Keep the compatibility implementation at the binding
// boundary so the pinned upstream submodule remains untouched.
#if !defined(__cpp_lib_ranges_starts_ends_with)
namespace std::ranges {

struct anitomy_kmp_starts_with_fn final {
    template <input_range Range1, input_range Range2, class Predicate = ranges::equal_to,
              class Projection1 = identity, class Projection2 = identity>
    [[nodiscard]] constexpr bool operator()(Range1&& range1, Range2&& range2,
                                            Predicate predicate = {},
                                            Projection1 projection1 = {},
                                            Projection2 projection2 = {}) const {
        auto first1 = ranges::begin(range1);
        const auto last1 = ranges::end(range1);
        auto first2 = ranges::begin(range2);
        const auto last2 = ranges::end(range2);

        for (; first2 != last2; ++first1, ++first2) {
            if (first1 == last1 ||
                !std::invoke(predicate, std::invoke(projection1, *first1),
                             std::invoke(projection2, *first2))) {
                return false;
            }
        }
        return true;
    }
};

inline constexpr anitomy_kmp_starts_with_fn starts_with{};

}  // namespace std::ranges
#endif
