package br.com.controlegastos.notifications.application;

/** Ao menos um envio falhou por motivo temporário; o evento fica pendente para nova tentativa. */
public class PushDeliveryException extends RuntimeException {

    public PushDeliveryException(int failures) {
        super("Falha temporária em " + failures + " envio(s) de push");
    }
}
