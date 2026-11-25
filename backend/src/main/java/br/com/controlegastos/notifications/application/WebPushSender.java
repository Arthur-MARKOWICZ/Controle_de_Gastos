package br.com.controlegastos.notifications.application;

/** Entrega uma mensagem Web Push a uma assinatura de navegador (ADR-0033). */
public interface WebPushSender {

    boolean isEnabled();

    /** Chave pública VAPID em Base64 URL-safe, ou {@code null} se o envio estiver desligado. */
    String publicKey();

    Outcome send(Target target, String payloadJson);

    record Target(String endpoint, String p256dh, String auth) {
    }

    enum Outcome {
        /** O push service aceitou a mensagem. */
        DELIVERED,
        /** A assinatura não existe mais (404/410) e deve ser apagada. */
        GONE,
        /** Falha de rede ou do push service; a assinatura continua válida. */
        FAILED
    }
}
