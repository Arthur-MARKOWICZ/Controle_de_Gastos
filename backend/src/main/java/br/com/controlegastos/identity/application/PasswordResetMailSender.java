package br.com.controlegastos.identity.application;

public interface PasswordResetMailSender {
    void sendPasswordReset(String recipient, String url);
    void sendPasswordChanged(String recipient);
}
