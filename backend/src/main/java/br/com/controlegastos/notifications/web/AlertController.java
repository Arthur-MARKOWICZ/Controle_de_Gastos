package br.com.controlegastos.notifications.web;

import br.com.controlegastos.notifications.application.AlertService;
import br.com.controlegastos.notifications.domain.InAppAlert;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/alerts")
class AlertController {

    private final AlertService alerts;
    private final Clock clock;

    AlertController(AlertService alerts, Clock clock) {
        this.alerts = alerts;
        this.clock = clock;
    }

    @GetMapping
    AlertListResponse list() {
        List<AlertResponse> items = alerts.listActive().stream().map(AlertResponse::from).toList();
        return new AlertListResponse(items);
    }

    @PostMapping("/{id}/dismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void dismiss(@PathVariable UUID id) {
        alerts.dismiss(id, clock.instant());
    }

    record AlertListResponse(List<AlertResponse> items) {
    }

    record AlertResponse(
            UUID id,
            String type,
            UUID envelopeId,
            String envelopeName,
            String referenceMonth,
            Instant createdAt
    ) {
        static AlertResponse from(InAppAlert alert) {
            return new AlertResponse(
                    alert.id(),
                    alert.type().name(),
                    alert.envelopeId(),
                    alert.envelopeName(),
                    YearMonth.from(alert.referenceMonth()).toString(),
                    alert.createdAt()
            );
        }
    }
}
