package pw.vodes.anitomy

/**
 * Parses an anime release or file name using Anitomy v2.
 *
 * Returned elements preserve their upstream order and may contain the same [ElementKind] more than
 * once.
 */
public fun parse(input: String, options: Options = Options()): List<Element> =
    parsePlatform(input, options)

internal expect fun parsePlatform(input: String, options: Options): List<Element>
