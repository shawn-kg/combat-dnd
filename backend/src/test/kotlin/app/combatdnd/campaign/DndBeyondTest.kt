package app.combatdnd.campaign

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DndBeyondTest {
    private val standard = "https://www.dndbeyond.com/characters/123456789"

    @Test
    fun `real character links are accepted and tidied`() {
        listOf(
            "https://www.dndbeyond.com/characters/123456789",
            "https://dndbeyond.com/characters/123456789",
            "http://www.dndbeyond.com/characters/123456789",
            "www.dndbeyond.com/characters/123456789",
            "  https://www.dndbeyond.com/characters/123456789/builder?tab=spells#top  ",
            "https://www.dndbeyond.com/characters/123456789/json",
            "https://www.dndbeyond.com/profile/SomeUser/characters/123456789",
            "https://WWW.DNDBEYOND.COM/characters/123456789",
        ).forEach { assertEquals(standard, dndBeyondCharacterUrl(it), it) }
    }

    @Test
    fun `anything else is refused`() {
        listOf(
            "https://evil.example/characters/123456789",
            "https://www.dndbeyond.com.evil.example/characters/123456789",
            "https://evil.example/?u=https://www.dndbeyond.com/characters/123456789",
            "https://user@www.dndbeyond.com/characters/123456789",
            "https://www.dndbeyond.com:8443/characters/123456789",
            "javascript:alert(1)//www.dndbeyond.com/characters/1",
            "ftp://www.dndbeyond.com/characters/123456789",
            "https://www.dndbeyond.com/monsters/123",
            "https://www.dndbeyond.com/characters/",
            "https://www.dndbeyond.com/characters/abc",
            "https://www.dndbeyond.com/characters/1234567890123",
            "https://www.dndbeyond.com/",
            "not a link",
            "",
        ).forEach { assertNull(dndBeyondCharacterUrl(it), it) }
    }
}
