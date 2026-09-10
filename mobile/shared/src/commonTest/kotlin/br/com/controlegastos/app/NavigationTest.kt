package br.com.controlegastos.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavigationTest {
    private val start: BackStack = listOf(Route.Overview)

    @Test
    fun `starts on the overview with nowhere to go back to`() {
        assertEquals(Route.Overview, start.current)
        assertFalse(start.canGoBack)
    }

    @Test
    fun `pushes a detail and comes back to where it started`() {
        val opened = start.push(Route.EnvelopeEntries("e-1", "Combustível"))

        assertEquals(Route.EnvelopeEntries("e-1", "Combustível"), opened.current)
        assertTrue(opened.canGoBack)
        assertEquals(Route.Overview, opened.pop().current)
    }

    @Test
    fun `ignores pushing the destination already on screen`() {
        val opened = start.push(Route.Reports)

        assertEquals(opened, opened.push(Route.Reports))
    }

    @Test
    fun `restarts the stack when a bottom bar destination is chosen`() {
        val deep = start.push(Route.More).push(Route.Reports)

        val switched = deep.selectTopLevel(Route.History)

        assertEquals(Route.History, switched.current)
        assertEquals(Route.History, switched.topLevel)
        assertFalse(switched.canGoBack)
    }

    @Test
    fun `keeps the bottom bar on the destination that started the stack`() {
        val deep = listOf<Route>(Route.More).push(Route.Reports)

        assertEquals(Route.More, deep.topLevel)
    }

    @Test
    fun `stays put when there is nothing to pop`() {
        assertEquals(start, start.pop())
    }
}
