package br.com.controlegastos.identity.infrastructure;

import br.com.controlegastos.identity.application.PasswordResetMailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
class PasswordResetMailConfiguration {
    @Bean
    @ConditionalOnProperty(name = "app.auth.password-reset.mail-enabled", havingValue = "true")
    PasswordResetMailSender gmailPasswordResetMailSender(JavaMailSender mail, @Value("${app.auth.password-reset.from}") String from) {
        return new GmailPasswordResetMailSender(mail, from);
    }

    @Bean
    @ConditionalOnProperty(name = "app.auth.password-reset.mail-enabled", havingValue = "false", matchIfMissing = true)
    PasswordResetMailSender disabledPasswordResetMailSender() {
        return new PasswordResetMailSender() {
            @Override public void sendPasswordReset(String recipient, String url) { }
            @Override public void sendPasswordChanged(String recipient) { }
        };
    }

    private static final class GmailPasswordResetMailSender implements PasswordResetMailSender {
        private final JavaMailSender mail;
        private final String from;
        private GmailPasswordResetMailSender(JavaMailSender mail, String from) { this.mail = mail; this.from = from; }
        @Override public void sendPasswordReset(String recipient, String url) {
            send(recipient, "Redefina sua senha", "Use este link em até 15 minutos:\n" + url);
        }
        @Override public void sendPasswordChanged(String recipient) {
            send(recipient, "Sua senha foi alterada", "Sua senha foi alterada. Se não foi você, procure suporte.");
        }
        private void send(String recipient, String subject, String text) {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from); message.setTo(recipient); message.setSubject(subject); message.setText(text);
            mail.send(message);
        }
    }
}
