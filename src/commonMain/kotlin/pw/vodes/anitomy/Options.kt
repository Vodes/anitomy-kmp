package pw.vodes.anitomy

/** Enables or disables Anitomy v2's optional parsing stages. */
public data class Options(
    public val parseEpisode: Boolean = true,
    public val parseEpisodeTitle: Boolean = true,
    public val parseFileChecksum: Boolean = true,
    public val parseFileExtension: Boolean = true,
    public val parsePart: Boolean = true,
    public val parseReleaseGroup: Boolean = true,
    public val parseSeason: Boolean = true,
    public val parseTitle: Boolean = true,
    public val parseVideoResolution: Boolean = true,
    public val parseYear: Boolean = true,
) {
    internal fun toBitMask(): Int {
        var mask = 0
        if (parseEpisode) mask = mask or (1 shl 0)
        if (parseEpisodeTitle) mask = mask or (1 shl 1)
        if (parseFileChecksum) mask = mask or (1 shl 2)
        if (parseFileExtension) mask = mask or (1 shl 3)
        if (parsePart) mask = mask or (1 shl 4)
        if (parseReleaseGroup) mask = mask or (1 shl 5)
        if (parseSeason) mask = mask or (1 shl 6)
        if (parseTitle) mask = mask or (1 shl 7)
        if (parseVideoResolution) mask = mask or (1 shl 8)
        if (parseYear) mask = mask or (1 shl 9)
        return mask
    }
}
