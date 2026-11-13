package br.com.controlegastos.ledger.application;

import br.com.controlegastos.ledger.domain.RecurringEntry;
import br.com.controlegastos.ledger.infrastructure.RecurringEntryRepository;
import br.com.controlegastos.ledger.infrastructure.RecurringOccurrenceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Gera uma ocorrência pendente por recorrência e competência. Só a competência
 * corrente é gerada: dias perdidos são recuperados no mês, meses passados não (ADR-0031).
 */
@Component
public class RecurringOccurrenceGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(RecurringOccurrenceGenerator.class);

    private final RecurringEntryRepository entries;
    private final RecurringOccurrenceRepository occurrences;
    private final Clock clock;

    public RecurringOccurrenceGenerator(RecurringEntryRepository entries, RecurringOccurrenceRepository occurrences,
                                        Clock clock) {
        this.entries = entries;
        this.occurrences = occurrences;
        this.clock = clock;
    }

    /** Recupera a janela perdida por indisponibilidade; uma falha aqui não impede a aplicação de subir. */
    @EventListener(ApplicationReadyEvent.class)
    public void generateOnStartup() {
        try {
            generateDue();
        } catch (RuntimeException failure) {
            LOG.warn("Geração de recorrências no arranque falhou ({}); o job diário tentará de novo",
                    failure.getClass().getSimpleName());
        }
    }

    @Scheduled(cron = "0 10 0 * * *", zone = "America/Sao_Paulo")
    public void generateDue() {
        LocalDate today = LocalDate.now(clock.withZone(LedgerService.BUSINESS_ZONE));
        Instant now = clock.instant();
        var candidates = entries.findGenerationCandidates(YearMonth.from(today).atDay(1));
        int created = 0;
        for (RecurringEntry entry : candidates) {
            var competence = entry.schedule().dueCompetence(entry.firstCompetence(), today);
            if (competence.isEmpty()) continue;
            created += occurrences.insertIfAbsent(UUID.randomUUID(), entry.id(), entry.envelopeId(),
                    competence.get().atDay(1), entry.schedule().dueDate(competence.get()),
                    entry.amount().amount(), now);
        }
        LOG.info("Geração de recorrências: {} ocorrência(s) criada(s) de {} recorrência(s) candidata(s)",
                created, candidates.size());
    }
}
