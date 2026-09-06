package br.com.controlegastos.identity.application;

import br.com.controlegastos.identity.domain.OAuthProvider;

public interface OAuthProviderClient {

    OAuthProvider provider();

    String authorizationUrl(String rawState);

    String exchangeCode(String code);

    ProviderProfile fetchProfile(String accessToken);

    record ProviderProfile(String providerUserId, String email) {
    }
}
