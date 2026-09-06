package br.com.controlegastos.identity.application;

import br.com.controlegastos.identity.domain.OAuthProvider;

public sealed interface OAuthCallbackOutcome {

    record LoggedIn(AuthenticationService.LoginOutcome outcome) implements OAuthCallbackOutcome {
    }

    record Linked(OAuthProvider provider) implements OAuthCallbackOutcome {
    }
}
