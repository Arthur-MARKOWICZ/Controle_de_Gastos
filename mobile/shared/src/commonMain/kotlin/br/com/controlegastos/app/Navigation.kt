package br.com.controlegastos.app

/**
 * Destinos do aplicativo.
 *
 * A navegação é uma pilha simples em vez de uma biblioteca: o módulo comum não
 * carrega dependências além do Compose e os destinos são poucos e estáveis.
 */
sealed interface Route {
    /** Destinos de primeiro nível, alcançáveis pela barra inferior. */
    sealed interface TopLevel : Route {
        val label: String
    }

    data object Overview : TopLevel {
        override val label = "Visão geral"
    }

    data object Envelopes : TopLevel {
        override val label = "Verbas"
    }

    data object Goals : TopLevel {
        override val label = "Metas"
    }

    data object History : TopLevel {
        override val label = "Gastos"
    }

    data object More : TopLevel {
        override val label = "Mais"
    }

    data object AnnualExpenses : Route
    data object Reports : Route
    data object Security : Route
    data object IncomeHistory : Route
    data class EnvelopeEntries(val envelopeId: String, val envelopeName: String) : Route
}

val TOP_LEVEL_ROUTES: List<Route.TopLevel> =
    listOf(Route.Overview, Route.Envelopes, Route.Goals, Route.History, Route.More)

/**
 * Pilha de navegação.
 *
 * Funções puras sobre uma lista para o estado poder viver num `mutableStateOf`
 * e a recomposição acontecer sozinha. Trocar de destino de primeiro nível
 * reinicia a pilha, para o botão voltar sempre levar a um lugar previsível em
 * vez de percorrer abas visitadas.
 */
typealias BackStack = List<Route>

val BackStack.current: Route get() = last()

val BackStack.topLevel: Route.TopLevel get() = first() as? Route.TopLevel ?: Route.Overview

val BackStack.canGoBack: Boolean get() = size > 1

fun BackStack.push(route: Route): BackStack = if (last() == route) this else this + route

fun BackStack.selectTopLevel(route: Route.TopLevel): BackStack = listOf(route)

/** Devolve a mesma pilha quando não havia para onde voltar. */
fun BackStack.pop(): BackStack = if (canGoBack) dropLast(1) else this
