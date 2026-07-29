package pw.vodes.anitomy

/**
 * One value recognized by Anitomy.
 *
 * [position] is the zero-based byte offset in the UTF-8 representation of the input, matching
 * Anitomy v2 rather than a Kotlin [String] character index.
 */
public data class Element(
    public val kind: ElementKind,
    public val value: String,
    public val position: Long,
)
