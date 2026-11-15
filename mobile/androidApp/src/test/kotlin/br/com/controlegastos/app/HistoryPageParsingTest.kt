package br.com.controlegastos.app

import kotlin.test.Test
import kotlin.test.assertEquals
import org.json.JSONObject

class HistoryPageParsingTest {
    private val item = """
        {"entry": {"id": "e1", "envelopeId": "env-1", "kind": "EXPENSE",
                   "amount": {"amount": "35.90", "currency": "BRL"},
                   "occurredAt": "2026-11-03", "description": "Farmácia", "deletedAt": null},
         "envelopeName": "Saúde", "purpose": "LIMIT", "role": "PARTICIPANT"}
    """

    @Test
    fun `reads the filtered total and the items`() {
        val body = JSONObject(
            """{"items": [$item], "page": 0, "size": 20, "hasNext": true,
                "matchedTotal": {"amount": "48.00", "currency": "BRL"}}""",
        )

        val page = readHistoryPage(body, requestedPage = 0)

        assertEquals(Money(4800), page.matchedTotal)
        assertEquals(true, page.hasNext)
        assertEquals("Farmácia", page.items.single().entry.description)
        assertEquals(Money(3590), page.items.single().entry.amount)
        assertEquals(EnvelopeRole.PARTICIPANT, page.items.single().role)
    }

    @Test
    fun `missing total from an older API reads as zero`() {
        val body = JSONObject("""{"items": [], "hasNext": false}""")

        val page = readHistoryPage(body, requestedPage = 2)

        assertEquals(Money(0), page.matchedTotal)
        assertEquals(2, page.page)
    }
}
