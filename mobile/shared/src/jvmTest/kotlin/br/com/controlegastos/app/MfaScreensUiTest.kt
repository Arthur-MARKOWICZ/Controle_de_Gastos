package br.com.controlegastos.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Telas de MFA: login com segundo fator e ativação (tarefa 6.3). */
@OptIn(ExperimentalTestApi::class)
class MfaScreensUiTest {

    private val enrollment = MfaEnrollmentStart(
        otpauthUri = "otpauth://totp/example",
        qrImageDataUri = "data:image/png;base64,aGVsbG8=",
        manualEntryKey = "JBSWY3DPEHPK3PXP",
        pendingExpiresAt = "2026-11-26T12:10:00Z",
    )

    @Test
    fun `confirma o codigo do aplicativo`() = runComposeUiTest {
        val codes = mutableListOf<String>()
        setContent {
            VerbasTheme(darkTheme = false) {
                MfaLoginScreen(onVerify = { code, done -> codes += code; done(null) }, onUseRecoveryCode = { _, _ -> error("n/a") })
            }
        }

        onNode(hasSetTextAction() and hasText("Código de 6 dígitos")).performTextInput("123456")
        onNodeWithText("Confirmar").performClick()
        waitForIdle()

        assertEquals(listOf("123456"), codes)
    }

    @Test
    fun `codigo vazio nao pode ser confirmado`() = runComposeUiTest {
        setContent {
            VerbasTheme(darkTheme = false) {
                MfaLoginScreen(onVerify = { _, _ -> error("não deveria enviar") }, onUseRecoveryCode = { _, _ -> error("n/a") })
            }
        }

        onNodeWithText("Confirmar").assertIsNotEnabled()
    }

    @Test
    fun `codigo errado mostra a mensagem e nao tenta o codigo de recuperacao`() = runComposeUiTest {
        var recoveryCalls = 0
        setContent {
            VerbasTheme(darkTheme = false) {
                MfaLoginScreen(
                    onVerify = { _, done -> done("Não foi possível confirmar o código.") },
                    onUseRecoveryCode = { _, _ -> recoveryCalls++ },
                )
            }
        }

        onNode(hasSetTextAction() and hasText("Código de 6 dígitos")).performTextInput("000000")
        onNodeWithText("Confirmar").performClick()
        waitForIdle()

        onNodeWithText("Não foi possível confirmar o código.").assertExists()
        assertEquals(0, recoveryCalls)
    }

    @Test
    fun `codigo de recuperacao usa o outro caminho e mostra a recusa`() = runComposeUiTest {
        val used = mutableListOf<String>()
        setContent {
            VerbasTheme(darkTheme = false) {
                MfaLoginScreen(
                    onVerify = { _, _ -> error("não deveria verificar TOTP") },
                    onUseRecoveryCode = { code, done -> used += code; done("Código de recuperação já usado.") },
                )
            }
        }

        onNodeWithText("Código de recuperação").performClick()
        onNode(hasSetTextAction() and hasText("Código de recuperação")).performTextInput("ABCDE-FGHJK")
        onNodeWithText("Confirmar").performClick()
        waitForIdle()

        assertEquals(listOf("ABCDE-FGHJK"), used)
        onNodeWithText("Código de recuperação já usado.").assertExists()
    }

    @Test
    fun `ativa o MFA e mostra os codigos antes de concluir`() = runComposeUiTest {
        var completed = false
        setContent {
            VerbasTheme(darkTheme = false) {
                MfaSettingsScreen(
                    onStartEnrollment = { _, done -> done(enrollment, null) },
                    onConfirmEnrollment = { _, done -> done(listOf("CODE0-XXXXX", "CODE1-XXXXX"), null) },
                    onComplete = { completed = true },
                    qrImageContent = {},
                )
            }
        }

        onNode(hasSetTextAction() and hasText("Senha atual")).performTextInput("frase segura de teste")
        onNodeWithText("Continuar").performClick()
        waitForIdle()
        onNodeWithText("JBSWY3DPEHPK3PXP", substring = true).assertExists()
        onNode(hasSetTextAction() and hasText("Código de 6 dígitos")).performTextInput("123456")
        onNodeWithText("Ativar MFA").performClick()
        waitForIdle()

        onNodeWithText("CODE0-XXXXX").assertExists()
        assertFalse(completed)
        onNodeWithText("Já guardei meus códigos, entrar novamente").performClick()
        assertTrue(completed)
    }

    @Test
    fun `senha vazia nao inicia a ativacao`() = runComposeUiTest {
        setContent {
            VerbasTheme(darkTheme = false) {
                MfaSettingsScreen({ _, _ -> error("não deveria iniciar") }, { _, _ -> error("n/a") }, {}, {})
            }
        }

        onNodeWithText("Continuar").assertIsNotEnabled()
    }

    @Test
    fun `senha errada na ativacao mostra a mensagem e nao revela a chave`() = runComposeUiTest {
        setContent {
            VerbasTheme(darkTheme = false) {
                MfaSettingsScreen(
                    onStartEnrollment = { _, done -> done(null, "Senha atual incorreta.") },
                    onConfirmEnrollment = { _, _ -> error("n/a") },
                    onComplete = {},
                    qrImageContent = {},
                )
            }
        }

        onNode(hasSetTextAction() and hasText("Senha atual")).performTextInput("senha errada de teste")
        onNodeWithText("Continuar").performClick()
        waitForIdle()

        onNodeWithText("Senha atual incorreta.").assertExists()
        onNodeWithText("JBSWY3DPEHPK3PXP", substring = true).assertDoesNotExist()
    }

    @Test
    fun `codigo errado na ativacao nao mostra codigos de recuperacao nem conclui`() = runComposeUiTest {
        var completed = false
        setContent {
            VerbasTheme(darkTheme = false) {
                MfaSettingsScreen(
                    onStartEnrollment = { _, done -> done(enrollment, null) },
                    onConfirmEnrollment = { _, done -> done(null, "Código inválido ou expirado. Gere um novo QR Code e tente novamente.") },
                    onComplete = { completed = true },
                    qrImageContent = {},
                )
            }
        }

        onNode(hasSetTextAction() and hasText("Senha atual")).performTextInput("frase segura de teste")
        onNodeWithText("Continuar").performClick()
        waitForIdle()
        onNode(hasSetTextAction() and hasText("Código de 6 dígitos")).performTextInput("000000")
        onNodeWithText("Ativar MFA").performClick()
        waitForIdle()

        onNodeWithText("Código inválido ou expirado. Gere um novo QR Code e tente novamente.").assertExists()
        onNodeWithText("CODE0-XXXXX").assertDoesNotExist()
        assertFalse(completed)
    }
}
