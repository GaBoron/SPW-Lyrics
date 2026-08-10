package dev.gaboron.spwlyrics.codec

import java.nio.charset.StandardCharsets
import java.util.HexFormat
import kotlin.test.Test
import kotlin.test.assertEquals

class QqDesLikeTest {
    @Test
    fun `decrypts known qq qrc block`() {
        val encrypted = HexFormat.of().parseHex("8A32307F5E3D4123819B65833270FB59")
        val key = "!@#)(*$%123ZXC!@!@#)(NHL".toByteArray(StandardCharsets.US_ASCII)

        val decrypted = HexFormat.of().withUpperCase().formatHex(QqDesLike.decrypt(encrypted, key))

        assertEquals("789C5D5A598F95D9757DB7E4FF50E2E9", decrypted)
    }
}
