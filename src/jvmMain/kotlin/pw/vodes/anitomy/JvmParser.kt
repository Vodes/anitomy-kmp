package pw.vodes.anitomy

import pw.vodes.anitomy.internal.JniBridge

internal actual fun parsePlatform(input: String, options: Options): List<Element> =
    JniBridge.parse(input, options.toBitMask())
