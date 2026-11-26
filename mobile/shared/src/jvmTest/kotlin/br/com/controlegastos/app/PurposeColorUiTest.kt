package br.com.controlegastos.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@OptIn(ExperimentalTestApi::class)
class PurposeColorUiTest {

    private fun colors(dark: Boolean, purposes: List<String>): Map<String, Color> {
        val result = mutableMapOf<String, Color>()
        runComposeUiTest {
            setContent {
                VerbasTheme(darkTheme = dark) { purposes.forEach { result[it] = purposeColor(it) } }
            }
            waitForIdle()
        }
        return result
    }

    private val known = EnvelopePurpose.entries.map { it.api }

    @Test
    fun `cada natureza tem a sua cor no tema claro`() {
        val light = colors(dark = false, known)

        assertEquals(known.size, light.values.toSet().size)
    }

    @Test
    fun `natureza desconhecida usa a cor de limite`() {
        val result = colors(dark = false, listOf(EnvelopePurpose.LIMIT.api, "DESCONHECIDA", ""))

        assertEquals(result[EnvelopePurpose.LIMIT.api], result["DESCONHECIDA"])
        assertEquals(result[EnvelopePurpose.LIMIT.api], result[""])
    }

    @Test
    fun `tema escuro troca a cor de cada natureza`() {
        val light = colors(dark = false, known)
        val dark = colors(dark = true, known)

        known.forEach { assertNotEquals(light[it], dark[it], "cor igual nos dois temas para $it") }
    }
}
