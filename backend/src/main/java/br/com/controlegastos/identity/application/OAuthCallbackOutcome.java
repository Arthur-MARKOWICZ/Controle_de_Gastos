package br.com.controlegastos.identity.application;

import br.com.controlegastos.identity.domain.OAuthClientKind;
import br.com.controlegastos.identity.domain.OAuthProvider;

public sealed interface OAuthCallbackOutcome {

    /** Login concluído, ou pendente de segundo fator, para o cliente informado. */
    record LoggedIn(AuthenticationService.LoginOutcome outcome, OAuthClientKind client)
            implements OAuthCallbackOutcome {
    }

    /**
     * Sessão criada para um cliente nativo e entregue por código de uso único.
     *
     * <p>O código vai na URL do App Link e é trocado em
     * {@code POST /api/v1/auth/oauth/mobile-handoff}.
     */
    record HandedOffToMobile(String code) implements OAuthCallbackOutcome {
    }

    record Linked(OAuthProvider provider) implements OAuthCallbackOutcome {
    }
}
