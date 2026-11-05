package br.com.controlegastos.notifications.application;

import java.util.UUID;

public class AlertNotFoundException extends RuntimeException {

    public AlertNotFoundException(UUID id) {
        super("Alerta não encontrado: " + id);
    }
}
