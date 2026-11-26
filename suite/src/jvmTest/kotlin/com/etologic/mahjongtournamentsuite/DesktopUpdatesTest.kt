package com.etologic.mahjongtournamentsuite

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopUpdatesTest {
    @Test
    fun picksTheNewestPublishedDesktopRelease() {
        val json = """
            [
              {"html_url": "https://github.com/o/r/releases/tag/desktop-v1.0.420", "tag_name": "desktop-v1.0.420", "draft": true, "prerelease": false},
              {"html_url": "https://github.com/o/r/releases/tag/desktop-v1.0.419", "tag_name": "desktop-v1.0.419", "draft": false, "immutable": false, "prerelease": true},
              {"html_url": "https://github.com/o/r/releases/tag/web-v9.9.9", "tag_name": "web-v9.9.9", "draft": false, "prerelease": false},
              {"html_url": "https://github.com/o/r/releases/tag/desktop-v1.0.99", "tag_name": "desktop-v1.0.99", "draft": false, "prerelease": false},
              {"html_url": "https://github.com/o/r/releases/tag/desktop-v1.0.415", "tag_name": "desktop-v1.0.415", "draft": false, "prerelease": false}
            ]
        """.trimIndent()

        assertEquals(DesktopRelease("1.0.415", "https://github.com/o/r/releases/tag/desktop-v1.0.415"), newestDesktopRelease(json))
    }

    @Test
    fun noReleaseInAnErrorOrAnEmptyAnswer() {
        assertNull(newestDesktopRelease("""{"message": "API rate limit exceeded"}"""))
        assertNull(newestDesktopRelease("[]"))
        assertNull(newestDesktopRelease("not json"))
    }

    @Test
    fun comparesVersionsByNumber() {
        assertTrue(isNewer("1.0.415", "1.0.414"))
        assertTrue(isNewer("1.1.0", "1.0.999"))
        assertFalse(isNewer("1.0.414", "1.0.414"))
        assertFalse(isNewer("1.0.99", "1.0.414"))
    }
}
