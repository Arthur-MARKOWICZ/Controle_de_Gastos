package br.com.controlegastos.notifications.application;

import br.com.controlegastos.notifications.domain.AlertType;
import java.util.UUID;

/**
 * Um alerta in-app foi criado e pode virar push. Publicado na transação do alerta;
 * o Event Publication Registry retoma o envio se ele falhar (ADR-0033).
 */
public record AlertRaised(UUID ownerId, AlertType type, String envelopePurpose) {
}
