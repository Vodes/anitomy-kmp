#include "anitomy_bridge.h"

#include <jni.h>

#include <cstdint>
#include <limits>
#include <memory>
#include <stdexcept>
#include <vector>

namespace {

constexpr uint8_t wire_magic[] = {'A', 'K', 'M', 'P'};
constexpr uint16_t wire_version = 1;

struct result_deleter {
    void operator()(anitomy_result* result) const noexcept {
        anitomy_result_destroy(result);
    }
};

using unique_result = std::unique_ptr<anitomy_result, result_deleter>;

void append_u16(std::vector<uint8_t>& output, const uint16_t value) {
    output.push_back(static_cast<uint8_t>(value));
    output.push_back(static_cast<uint8_t>(value >> 8));
}

void append_u32(std::vector<uint8_t>& output, const uint32_t value) {
    for (unsigned int shift = 0; shift < 32; shift += 8) {
        output.push_back(static_cast<uint8_t>(value >> shift));
    }
}

void append_u64(std::vector<uint8_t>& output, const uint64_t value) {
    for (unsigned int shift = 0; shift < 64; shift += 8) {
        output.push_back(static_cast<uint8_t>(value >> shift));
    }
}

void throw_java(JNIEnv* env, const char* class_name, const char* message) {
    const jclass exception_class = env->FindClass(class_name);
    if (exception_class != nullptr) {
        env->ThrowNew(exception_class, message);
    }
}

}  // namespace

extern "C" JNIEXPORT jbyteArray JNICALL
Java_pw_vodes_anitomy_internal_JniBindings_parse(
    JNIEnv* env,
    jclass,
    const jbyteArray input,
    const jint options
) {
    if (input == nullptr) {
        throw_java(env, "java/lang/NullPointerException", "input");
        return nullptr;
    }

    try {
        const jsize input_length = env->GetArrayLength(input);
        std::vector<jbyte> input_bytes(static_cast<size_t>(input_length));
        if (input_length > 0) {
            env->GetByteArrayRegion(input, 0, input_length, input_bytes.data());
            if (env->ExceptionCheck()) {
                return nullptr;
            }
        }

        unique_result result{anitomy_parse_utf8(
            reinterpret_cast<const char*>(input_bytes.data()),
            static_cast<size_t>(input_length),
            static_cast<uint32_t>(options)
        )};
        if (result == nullptr) {
            throw_java(env, "java/lang/OutOfMemoryError", "Anitomy could not allocate a parse result");
            return nullptr;
        }

        const size_t count = anitomy_result_count(result.get());
        if (count > std::numeric_limits<uint32_t>::max()) {
            throw std::length_error{"Too many parsed elements"};
        }

        std::vector<uint8_t> output;
        output.insert(output.end(), std::begin(wire_magic), std::end(wire_magic));
        append_u16(output, wire_version);
        append_u16(output, 0);
        append_u32(output, static_cast<uint32_t>(count));

        for (size_t index = 0; index < count; ++index) {
            size_t value_length = 0;
            const char* value = anitomy_result_value(result.get(), index, &value_length);
            if (value_length > std::numeric_limits<uint32_t>::max()) {
                throw std::length_error{"Parsed element is too long"};
            }

            append_u32(output, static_cast<uint32_t>(anitomy_result_kind(result.get(), index)));
            append_u64(output, anitomy_result_position(result.get(), index));
            append_u32(output, static_cast<uint32_t>(value_length));
            output.insert(
                output.end(),
                reinterpret_cast<const uint8_t*>(value),
                reinterpret_cast<const uint8_t*>(value) + value_length
            );
        }

        if (output.size() > static_cast<size_t>(std::numeric_limits<jsize>::max())) {
            throw std::length_error{"Encoded parse result is too large"};
        }

        const auto output_length = static_cast<jsize>(output.size());
        const jbyteArray bytes = env->NewByteArray(output_length);
        if (bytes == nullptr) {
            return nullptr;
        }
        env->SetByteArrayRegion(
            bytes,
            0,
            output_length,
            reinterpret_cast<const jbyte*>(output.data())
        );
        return env->ExceptionCheck() ? nullptr : bytes;
    } catch (const std::bad_alloc&) {
        throw_java(env, "java/lang/OutOfMemoryError", "Anitomy JNI bridge ran out of memory");
    } catch (const std::exception& exception) {
        throw_java(env, "java/lang/IllegalStateException", exception.what());
    } catch (...) {
        throw_java(env, "java/lang/IllegalStateException", "Unknown Anitomy JNI bridge failure");
    }
    return nullptr;
}
