package br.com.controlegastos.identity.domain;

/**
 * Como a sessão do login social é entregue ao cliente.
 *
 * <p>{@code WEB} recebe o refresh em cookie e é redirecionado para a aplicação
 * web. {@code MOBILE} recebe um código de handoff de uso único por App Link e o
 * troca pelos tokens, porque um aplicativo nativo não compartilha o cookie do
 * navegador do sistema. Ver ADR-020.
 */
public enum OAuthClientKind {
    WEB,
    MOBILE
}
