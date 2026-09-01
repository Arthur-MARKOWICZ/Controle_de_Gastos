package br.com.controlegastos.reporting.application;

import br.com.controlegastos.ledger.application.LedgerReportingQuery;
import br.com.controlegastos.reporting.domain.ReportRange;
import br.com.controlegastos.shared.money.Money;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ReportingService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private final LedgerReportingQuery ledger;

    public ReportingService(LedgerReportingQuery ledger) {
        this.ledger = ledger;
    }

    public ReportDocument prepare(ReportType type, ReportRange range, ReportFormat format) {
        if (type.monthly()) range.requireWholeMonths();
        return switch (type) {
            case EXPENSES_BY_PURPOSE -> expensesByPurpose(range, format);
            case LIMIT_EXCEEDED_MONTHS -> limitExceededMonths(range, format);
            case GOALS_BELOW_TARGET -> goalsBelowTarget(range, format);
        };
    }

    private ReportDocument expensesByPurpose(ReportRange range, ReportFormat format) {
        List<LedgerReportingQuery.ReportEnvelope> envelopes = ledger.visibleEnvelopes();
        List<LedgerReportingQuery.ReportEntry> entries = ledger.activeEntries(range.from(), range.to());
        Map<UUID, Money> expenses = totalsByEnvelope(entries, "EXPENSE");
        Map<UUID, Integer> expenseCount = countExpensesByEnvelope(entries);
        Map<String, Money> byPurpose = new java.util.HashMap<>();
        for (LedgerReportingQuery.ReportEnvelope envelope : envelopes) {
            byPurpose.merge(envelope.purpose(), expenses.getOrDefault(envelope.id(), Money.zero()), Money::add);
        }
        List<List<String>> rows = envelopes.stream()
                .sorted(Comparator.comparing(LedgerReportingQuery.ReportEnvelope::purpose).thenComparing(LedgerReportingQuery.ReportEnvelope::name))
                        .map(envelope -> List.of(envelope.purpose(), safeText(envelope.name()),
                        money(expenses.getOrDefault(envelope.id(), Money.zero())),
                        String.valueOf(expenseCount.getOrDefault(envelope.id(), 0)),
                        money(byPurpose.getOrDefault(envelope.purpose(), Money.zero()))))
                .toList();
        return document(ReportType.EXPENSES_BY_PURPOSE, range, format, "Gastos por tipo",
                List.of("tipo", "verba", "gastos_brl", "quantidade_de_gastos", "total_do_tipo_brl"), rows);
    }

    private ReportDocument limitExceededMonths(ReportRange range, ReportFormat format) {
        List<LedgerReportingQuery.ReportEntry> entries = ledger.activeEntries(range.from(), range.to());
        List<List<String>> rows = new ArrayList<>();
        for (LedgerReportingQuery.ReportEnvelope envelope : ledger.visibleEnvelopes()) {
            if (!"LIMIT".equals(envelope.purpose())) continue;
            for (YearMonth month : eligibleMonths(envelope, range)) {
                Money closing = ledger.availableAt(envelope.id(), month.atEndOfMonth());
                if (!closing.isNegative()) continue;
                rows.add(List.of(month.toString(), safeText(envelope.name()), money(envelope.baseAmount()),
                        money(total(entries, envelope.id(), month, "CONTRIBUTION")),
                        money(total(entries, envelope.id(), month, "EXPENSE")), money(closing)));
            }
        }
        return document(ReportType.LIMIT_EXCEEDED_MONTHS, range, format, "Limites extrapolados",
                List.of("mes", "verba", "limite_mensal_brl", "aportes_brl", "gastos_brl", "saldo_fechamento_brl"), rows);
    }

    private ReportDocument goalsBelowTarget(ReportRange range, ReportFormat format) {
        List<LedgerReportingQuery.ReportEntry> entries = ledger.activeEntries(range.from(), range.to());
        List<List<String>> rows = new ArrayList<>();
        for (LedgerReportingQuery.ReportEnvelope envelope : ledger.visibleEnvelopes()) {
            if (!"GOAL".equals(envelope.purpose())) continue;
            List<YearMonth> months = eligibleMonths(envelope, range);
            Money expected = repeat(envelope.baseAmount(), months.size());
            Money contributed = total(entries, envelope.id(), null, "CONTRIBUTION");
            if (contributed.compareTo(expected) < 0) {
                rows.add(List.of(safeText(envelope.name()), String.valueOf(months.size()), money(expected),
                        money(contributed), money(expected.subtract(contributed))));
            }
        }
        return document(ReportType.GOALS_BELOW_TARGET, range, format, "Metas abaixo",
                List.of("verba", "meses_elegiveis", "meta_esperada_brl", "aportes_brl", "faltante_brl"), rows);
    }

    private List<YearMonth> eligibleMonths(LedgerReportingQuery.ReportEnvelope envelope, ReportRange range) {
        YearMonth first = YearMonth.from(envelope.createdAt().atZone(BUSINESS_ZONE));
        YearMonth last = envelope.archivedAt() == null ? YearMonth.from(range.to())
                : YearMonth.from(envelope.archivedAt().atZone(BUSINESS_ZONE));
        return range.months().stream().filter(month -> !month.isBefore(first) && !month.isAfter(last)).toList();
    }

    private Map<UUID, Integer> countExpensesByEnvelope(List<LedgerReportingQuery.ReportEntry> entries) {
        return entries.stream().filter(entry -> "EXPENSE".equals(entry.kind()))
                .collect(Collectors.toMap(LedgerReportingQuery.ReportEntry::envelopeId, entry -> 1, Integer::sum));
    }

    private Map<UUID, Money> totalsByEnvelope(List<LedgerReportingQuery.ReportEntry> entries, String kind) {
        return entries.stream().filter(entry -> kind.equals(entry.kind()))
                .collect(Collectors.toMap(LedgerReportingQuery.ReportEntry::envelopeId, LedgerReportingQuery.ReportEntry::amount, Money::add));
    }

    private Money total(List<LedgerReportingQuery.ReportEntry> entries, UUID envelopeId, YearMonth month, String kind) {
        return entries.stream().filter(entry -> entry.envelopeId().equals(envelopeId) && kind.equals(entry.kind()))
                .filter(entry -> month == null || YearMonth.from(entry.occurredAt()).equals(month))
                .map(LedgerReportingQuery.ReportEntry::amount).reduce(Money.zero(), Money::add);
    }

    private Money repeat(Money amount, int times) {
        Money result = Money.zero();
        for (int index = 0; index < times; index++) result = result.add(amount);
        return result;
    }

    private ReportDocument document(ReportType type, ReportRange range, ReportFormat format, String sheetName,
                                    List<String> headers, List<List<String>> rows) {
        String filename = type.filenamePrefix() + "_" + range.from() + "_" + range.to() + "." + format.extension();
        return new ReportDocument(filename, format, sheetName, headers, rows);
    }

    private String money(Money value) {
        DecimalFormat formatter = new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(PT_BR));
        formatter.setGroupingUsed(false);
        return formatter.format(value.amount());
    }

    private String safeText(String value) {
        if (value != null && !value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0) return "'" + value;
        return value;
    }
}
