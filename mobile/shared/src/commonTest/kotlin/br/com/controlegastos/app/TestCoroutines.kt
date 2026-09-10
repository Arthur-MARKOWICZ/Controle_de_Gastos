package br.com.controlegastos.app

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/**
 * Executa um bloco suspenso em testes sem trazer `kotlinx-coroutines-test`.
 *
 * Os dubles do módulo respondem sem suspender de verdade, então o bloco termina
 * antes de `startCoroutine` retornar. Um duble que suspenda de fato falharia
 * silenciosamente aqui — mantenha-os síncronos.
 */
internal fun runSuspend(block: suspend () -> Unit) {
    var completed = false
    var failure: Throwable? = null
    block.startCoroutine(object : Continuation<Unit> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<Unit>) {
            completed = true
            failure = result.exceptionOrNull()
        }
    })
    failure?.let { throw it }
    check(completed) { "O bloco suspendeu e não terminou: use dubles síncronos." }
}
