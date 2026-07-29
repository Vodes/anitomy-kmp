package pw.vodes.anitomy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnitomyTest {
    @Test
    fun parsesUpstreamExampleInOrder() {
        assertEquals(
            listOf(
                Element(ElementKind.RELEASE_GROUP, "TaigaSubs", 1),
                Element(ElementKind.TITLE, "Toradora!", 12),
                Element(ElementKind.YEAR, "2008", 23),
                Element(ElementKind.EPISODE, "01", 31),
                Element(ElementKind.RELEASE_VERSION, "2", 34),
                Element(ElementKind.EPISODE_TITLE, "Tiger and Dragon", 38),
                Element(ElementKind.VIDEO_RESOLUTION, "1080p", 56),
                Element(ElementKind.VIDEO_TERM, "H.264", 62),
                Element(ElementKind.AUDIO_TERM, "FLAC", 68),
                Element(ElementKind.FILE_CHECKSUM, "BAD7A16A", 74),
                Element(ElementKind.FILE_EXTENSION, "mkv", 84),
            ),
            parse(
                "[TaigaSubs]_Toradora!_(2008)_-_01v2_-_Tiger_and_Dragon_" +
                    "[1080p_H.264_FLAC][BAD7A16A].mkv",
            ),
        )
    }

    @Test
    fun fixesTheReferencedAnitomyJRegression() {
        val elements = parse("[Vodes] Fumetsu no Anata e - S01E15.mkv")
        assertEquals("Vodes", elements.valueOf(ElementKind.RELEASE_GROUP))
        assertEquals("Fumetsu no Anata e", elements.valueOf(ElementKind.TITLE))
        assertEquals("01", elements.valueOf(ElementKind.SEASON))
        assertEquals("15", elements.valueOf(ElementKind.EPISODE))
    }

    @Test
    fun exposesUtf8BytePositions() {
        assertEquals(
            listOf(
                Element(ElementKind.RELEASE_GROUP, "字幕", 1),
                Element(ElementKind.TITLE, "Título", 9),
                Element(ElementKind.EPISODE, "01", 19),
                Element(ElementKind.FILE_EXTENSION, "mkv", 22),
            ),
            parse("[字幕] Título - 01.mkv"),
        )
    }

    @Test
    fun supportsEmptyAndEmbeddedNullInput() {
        assertTrue(parse("").isEmpty())
        assertEquals("Show\u0000Name", parse("Show\u0000Name - 01.mkv").valueOf(ElementKind.TITLE))
    }

    @Test
    fun forwardsEveryParserOption() {
        val input = "[Group] Show (2024) S02E03 Part 1 [1080p] [ABCDEF12].mkv"
        val disabled =
            parse(
                input,
                Options(
                    parseEpisode = false,
                    parseEpisodeTitle = false,
                    parseFileChecksum = false,
                    parseFileExtension = false,
                    parsePart = false,
                    parseReleaseGroup = false,
                    parseSeason = false,
                    parseTitle = false,
                    parseVideoResolution = false,
                    parseYear = false,
                ),
            )
        val disabledKinds =
            setOf(
                ElementKind.EPISODE,
                ElementKind.EPISODE_TITLE,
                ElementKind.FILE_CHECKSUM,
                ElementKind.FILE_EXTENSION,
                ElementKind.PART,
                ElementKind.RELEASE_GROUP,
                ElementKind.SEASON,
                ElementKind.TITLE,
                ElementKind.VIDEO_RESOLUTION,
                ElementKind.YEAR,
            )
        assertFalse(disabled.any { it.kind in disabledKinds })
    }

    @Test
    fun mapsEveryUpstreamElementKind() {
        val coverageInputs =
            styxCases.map(Case::input) +
                listOf(
                    "[PS5] Anime - 01.mkv",
                    "[Juuni.Kokki]-(Les.12.Royaumes)-[Ep.24]-[x264+OGG]-" +
                        "[JAP+FR+Sub.FR]-[Chap]-[AzF].mkv",
                    "[chibi-Doki] Seikon no Qwaser - 13v0 " +
                        "(Uncensored Director's Cut) [988DB090].mkv",
                    "[Nishi-Taku] Tamayura ~graduation photo~ Movie Part 1 " +
                        "[BD][720p][98965607].mkv",
                    "Vol.01",
                    "[TaigaSubs]_Toradora!_(2008)_-_01v2_-_Tiger_and_Dragon_" +
                        "[1280x720_H.264_FLAC][1234ABCD].mkv",
                )
        val parsedKinds = coverageInputs.flatMap(::parse).map(Element::kind).toSet()
        assertEquals(ElementKind.entries.toSet(), parsedKinds)
    }

    @Test
    fun matchesPinnedV2ForStyxAndWorkaroundCases() {
        styxCases.forEach { case ->
            assertEquals(case.expected, parse(case.input), case.input)
        }
    }

    @Test
    fun repeatedlyParsesWithoutLeakingResultOwnership() {
        repeat(1_000) {
            val elements = parse("[Group] Show - S01E02v3 [1080p].mkv")
            assertEquals("02", elements.valueOf(ElementKind.EPISODE))
            assertEquals("3", elements.valueOf(ElementKind.RELEASE_VERSION))
        }
    }
}

private fun List<Element>.valueOf(kind: ElementKind): String? =
    firstOrNull { it.kind == kind }?.value

private data class Case(
    val input: String,
    val expected: List<Element>,
)

private fun case(input: String, expected: String): Case =
    Case(
        input,
        expected
            .trimIndent()
            .lineSequence()
            .filter(String::isNotBlank)
            .map { line ->
                val (kind, value, position) = line.split('|', limit = 3).map(String::trim)
                Element(ElementKind.valueOf(kind), value, position.toLong())
            }.toList(),
    )

private val styxCases =
    listOf(
        case(
            "The Age of Cosmos Exploration - S01E12 - 1080p WEB H.264 " +
                "-NanDesuKa (B-Global).mkv",
            """
            TITLE | The Age of Cosmos Exploration | 0
            SEASON | 01 | 33
            EPISODE | 12 | 36
            VIDEO_RESOLUTION | 1080p | 41
            SOURCE | WEB | 47
            VIDEO_TERM | H.264 | 51
            RELEASE_GROUP | B-Global | 69
            FILE_EXTENSION | mkv | 79
            """,
        ),
        case(
            "[SubsPlus+] Oshi no Ko - S02E01v2 (NF WEB 1080p AVC AAC) [E01A6580].mkv",
            """
            RELEASE_GROUP | SubsPlus+ | 1
            TITLE | Oshi no Ko | 12
            SEASON | 02 | 26
            EPISODE | 01 | 29
            RELEASE_VERSION | 2 | 32
            SOURCE | NF | 35
            SOURCE | WEB | 38
            VIDEO_RESOLUTION | 1080p | 42
            VIDEO_TERM | AVC | 48
            AUDIO_TERM | AAC | 52
            FILE_CHECKSUM | E01A6580 | 58
            FILE_EXTENSION | mkv | 68
            """,
        ),
        case(
            "The.Misfit.of.Demon.King.Academy.S02E23.1080p.CR.WEB-DL." +
                "AAC2.0.H.264-NanDesuKa.mkv",
            """
            TITLE | The Misfit of Demon King Academy | 0
            SEASON | 02 | 34
            EPISODE | 23 | 37
            VIDEO_RESOLUTION | 1080p | 40
            SOURCE | CR | 46
            SOURCE | WEB-DL | 49
            AUDIO_TERM | AAC | 56
            AUDIO_TERM | 2.0 | 59
            VIDEO_TERM | H.264 | 63
            RELEASE_GROUP | NanDesuKa | 69
            FILE_EXTENSION | mkv | 79
            """,
        ),
        case(
            "Invincible.2021.S02E01.A.LESSON.FOR.YOUR.NEXT.LIFE.1080p." +
                "AMZN.WEB-DL.DDP5.1.H.264-FLUX.mkv",
            """
            TITLE | Invincible 2021 | 0
            SEASON | 02 | 17
            EPISODE | 01 | 20
            EPISODE_TITLE | A LESSON FOR YOUR NEXT LIFE | 23
            VIDEO_RESOLUTION | 1080p | 51
            SOURCE | AMZN | 57
            SOURCE | WEB-DL | 62
            AUDIO_TERM | DDP | 69
            AUDIO_TERM | 5.1 | 72
            VIDEO_TERM | H.264 | 76
            RELEASE_GROUP | FLUX | 82
            FILE_EXTENSION | mkv | 87
            """,
        ),
        case(
            "Tensei shitara Slime Datta Ken - S01E01 (BD 1080p HEVC) [Vodes].mkv",
            """
            TITLE | Tensei shitara Slime Datta Ken | 0
            SEASON | 01 | 34
            EPISODE | 01 | 37
            SOURCE | BD | 41
            VIDEO_RESOLUTION | 1080p | 44
            VIDEO_TERM | HEVC | 50
            RELEASE_GROUP | Vodes | 57
            FILE_EXTENSION | mkv | 64
            """,
        ),
        case(
            "Whisper.Me.a.Love.Song.S01E08.1080p.WEBRip.DDP2.0.x265-smol.mkv",
            """
            TITLE | Whisper Me a Love Song | 0
            SEASON | 01 | 24
            EPISODE | 08 | 27
            VIDEO_RESOLUTION | 1080p | 30
            SOURCE | WEBRip | 36
            AUDIO_TERM | DDP | 43
            AUDIO_TERM | 2.0 | 46
            VIDEO_TERM | x265 | 50
            RELEASE_GROUP | smol | 55
            FILE_EXTENSION | mkv | 60
            """,
        ),
        case(
            "Given.S01E10.1080p.BluRay.Opus2.0.x265-smol.mkv",
            """
            TITLE | Given | 0
            SEASON | 01 | 7
            EPISODE | 10 | 10
            VIDEO_RESOLUTION | 1080p | 13
            SOURCE | BluRay | 19
            VIDEO_TERM | x265 | 34
            RELEASE_GROUP | smol | 39
            FILE_EXTENSION | mkv | 44
            """,
        ),
        case(
            "[HatSubs] One Piece 1088.5 (WEB 1080p) [BAACCC99].mkv",
            """
            RELEASE_GROUP | HatSubs | 1
            TITLE | One Piece | 10
            EPISODE | 1088.5 | 20
            SOURCE | WEB | 28
            VIDEO_RESOLUTION | 1080p | 32
            FILE_CHECKSUM | BAACCC99 | 40
            FILE_EXTENSION | mkv | 50
            """,
        ),
        case(
            "KONOSUBA.-Gods.blessing.on.this.wonderful.world!.S03E11." +
                "Gods.Blessings.for.These.Unchanging.Days!.1080p.CR.WEB-DL." +
                "DUAL.AAC2.0.H.264.MSubs-ToonsHub.mkv",
            """
            TITLE | KONOSUBA -Gods blessing on this wonderful world! | 0
            SEASON | 03 | 50
            EPISODE | 11 | 53
            EPISODE_TITLE | Gods Blessings for These Unchanging Days! | 56
            VIDEO_RESOLUTION | 1080p | 98
            SOURCE | CR | 104
            SOURCE | WEB-DL | 107
            AUDIO_TERM | AAC | 119
            AUDIO_TERM | 2.0 | 122
            VIDEO_TERM | H.264 | 126
            RELEASE_GROUP | ToonsHub | 138
            FILE_EXTENSION | mkv | 147
            """,
        ),
        case(
            "Yubisaki to Renren - S01E09 - DUAL 480p WEB x264 -NanDesuKa (CR).mkv",
            """
            TITLE | Yubisaki to Renren | 0
            SEASON | 01 | 22
            EPISODE | 09 | 25
            EPISODE_TITLE | DUAL | 30
            VIDEO_RESOLUTION | 480p | 35
            SOURCE | WEB | 40
            VIDEO_TERM | x264 | 44
            SOURCE | CR | 61
            FILE_EXTENSION | mkv | 65
            """,
        ),
        case(
            "[SubsPlus+] 2.5 Dimensional Seduction - S01E01 " +
                "(CR WEB 1080p AVC EAC3) | 2.5 Jigen no Ririsa",
            """
            RELEASE_GROUP | SubsPlus+ | 1
            TITLE | 2.5 Dimensional Seduction | 12
            SEASON | 01 | 41
            EPISODE | 01 | 44
            SOURCE | CR | 48
            SOURCE | WEB | 51
            VIDEO_RESOLUTION | 1080p | 55
            VIDEO_TERM | AVC | 61
            AUDIO_TERM | EAC3 | 65
            """,
        ),
        case(
            "[SubsPlus+] 2.5 Dimensional Seduction - S01E01 (CR WEB 1080p AVC EAC3).mkv",
            """
            RELEASE_GROUP | SubsPlus+ | 1
            TITLE | 2.5 Dimensional Seduction | 12
            SEASON | 01 | 41
            EPISODE | 01 | 44
            SOURCE | CR | 48
            SOURCE | WEB | 51
            VIDEO_RESOLUTION | 1080p | 55
            VIDEO_TERM | AVC | 61
            AUDIO_TERM | EAC3 | 65
            FILE_EXTENSION | mkv | 71
            """,
        ),
        case(
            "[SubsPlease] NieR Automata Ver1.1a - 13 (1080p) [DF36D5E3].mkv",
            """
            RELEASE_GROUP | SubsPlease | 1
            TITLE | NieR Automata Ver1.1a | 13
            EPISODE | 13 | 37
            VIDEO_RESOLUTION | 1080p | 41
            FILE_CHECKSUM | DF36D5E3 | 49
            FILE_EXTENSION | mkv | 59
            """,
        ),
        case(
            "2.5 Jigen no Ririsa E01 [1080p][AAC][JapDub][GerSub][Web-DL].mkv",
            """
            TITLE | 2.5 Jigen no Ririsa | 0
            EPISODE | 01 | 21
            VIDEO_RESOLUTION | 1080p | 25
            AUDIO_TERM | AAC | 32
            AUDIO_TERM | JapDub | 37
            SUBTITLES | GerSub | 45
            SOURCE | Web-DL | 53
            FILE_EXTENSION | mkv | 61
            """,
        ),
        case(
            "NieRAutomata Ver 1.1a S2E01 [1080p][AAC][JapDub][GerEngSub][Web-DL].mkv",
            """
            TITLE | NieRAutomata Ver 1.1a | 0
            SEASON | 2 | 23
            EPISODE | 01 | 25
            VIDEO_RESOLUTION | 1080p | 29
            AUDIO_TERM | AAC | 36
            AUDIO_TERM | JapDub | 41
            RELEASE_GROUP | GerEngSub | 49
            SOURCE | Web-DL | 60
            FILE_EXTENSION | mkv | 68
            """,
        ),
        case(
            "CITY.THE.ANIMATION.S01E02.2.1080p.AMZN.WEB-DL.MULTi." +
                "DDP2.0.H.264-VARYG.mkv",
            """
            TITLE | CITY THE ANIMATION | 0
            SEASON | 01 | 20
            EPISODE | 02 | 23
            VIDEO_RESOLUTION | 1080p | 28
            SOURCE | AMZN | 34
            SOURCE | WEB-DL | 39
            AUDIO_TERM | DDP | 52
            AUDIO_TERM | 2.0 | 55
            VIDEO_TERM | H.264 | 59
            RELEASE_GROUP | VARYG | 65
            FILE_EXTENSION | mkv | 71
            """,
        ),
        case(
            "Show.Name.S01E03.REPACK.1080p.WEB-DL-GROUP.mkv",
            """
            TITLE | Show Name | 0
            SEASON | 01 | 11
            EPISODE | 03 | 14
            RELEASE_INFORMATION | REPACK | 17
            VIDEO_RESOLUTION | 1080p | 24
            SOURCE | WEB-DL | 30
            RELEASE_GROUP | GROUP | 37
            FILE_EXTENSION | mkv | 43
            """,
        ),
        case(
            "Show.Name.S01E03.REPACK2.1080p.WEB-DL-GROUP.mkv",
            """
            TITLE | Show Name | 0
            SEASON | 01 | 11
            EPISODE | 03 | 14
            EPISODE_TITLE | REPACK2 | 17
            VIDEO_RESOLUTION | 1080p | 25
            SOURCE | WEB-DL | 31
            RELEASE_GROUP | GROUP | 38
            FILE_EXTENSION | mkv | 44
            """,
        ),
        case(
            "Show.Name.S00E04.1080p.WEB-DL-GROUP.mkv",
            """
            TITLE | Show Name | 0
            SEASON | 00 | 11
            EPISODE | 04 | 14
            VIDEO_RESOLUTION | 1080p | 17
            SOURCE | WEB-DL | 23
            RELEASE_GROUP | GROUP | 30
            FILE_EXTENSION | mkv | 36
            """,
        ),
    )
