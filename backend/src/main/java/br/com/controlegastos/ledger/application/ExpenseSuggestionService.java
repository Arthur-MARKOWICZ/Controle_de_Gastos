package br.com.controlegastos.ledger.application;

import br.com.controlegastos.envelopes.application.EnvelopeService;
import br.com.controlegastos.envelopes.domain.Envelope;
import br.com.controlegastos.identity.application.AuthenticationService;
import br.com.controlegastos.ledger.domain.LedgerEntry;
import br.com.controlegastos.ledger.infrastructure.LedgerEntryRepository;
import br.com.controlegastos.shared.money.Money;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sugestões de verba/valor para lançamento rápido (frequência DOW + hora, sem ML/cache).
 */
@Service
public class ExpenseSuggestionService {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final int WINDOW_DAYS = 90;
    private static final int MIN_EXPENSES = 2;
    private static final int MIN_AMOUNT_OCCURRENCES = 2;
    private static final int MAX_ITEMS = 3;

    private final EnvelopeService envelopes;
    private final LedgerEntryRepository entries;
    private final AuthenticationService authentication;
    private final Clock clock;

    public ExpenseSuggestionService(EnvelopeService envelopes, LedgerEntryRepository entries,
                                    AuthenticationService authentication, Clock clock) {
        this.envelopes = envelopes;
        this.entries = entries;
        this.authentication = authentication;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SuggestionResult suggest() {
        ZonedDateTime asOf = ZonedDateTime.now(clock.withZone(BUSINESS_ZONE));
        UUID userId = authentication.currentUserId();
        LocalDate today = asOf.toLocalDate();
        LocalDate from = today.minusDays(WINDOW_DAYS);

        Map<UUID, Envelope> visible = new HashMap<>();
        for (Envelope envelope : envelopes.listVisible()) {
            visible.put(envelope.id(), envelope);
        }
        if (visible.isEmpty()) {
            return new SuggestionResult(asOf, List.of());
        }

        List<LedgerEntry> history = entries.findActiveExpensesByAuthorBetween(userId, from, today);
        Map<UUID, List<LedgerEntry>> byEnvelope = new HashMap<>();
        for (LedgerEntry entry : history) {
            if (!visible.containsKey(entry.envelopeId())) {
                continue;
            }
            byEnvelope.computeIfAbsent(entry.envelopeId(), ignored -> new ArrayList<>()).add(entry);
        }

        DayOfWeek nowDow = asOf.getDayOfWeek();
        int nowHour = asOf.getHour();

        List<Ranked> ranked = new ArrayList<>();
        for (Map.Entry<UUID, List<LedgerEntry>> group : byEnvelope.entrySet()) {
            List<LedgerEntry> groupEntries = group.getValue();
            if (groupEntries.size() < MIN_EXPENSES) {
                continue;
            }
            int score = 0;
            Instant mostRecent = Instant.EPOCH;
            for (LedgerEntry entry : groupEntries) {
                DayOfWeek dow = entry.occurredAt().getDayOfWeek();
                int hour = entry.createdAt().atZone(BUSINESS_ZONE).getHour();
                if (dow.equals(nowDow) && hour == nowHour) {
                    score += 3;
                } else if (dow.equals(nowDow)) {
                    score += 2;
                } else {
                    score += 1;
                }
                if (entry.createdAt().isAfter(mostRecent)) {
                    mostRecent = entry.createdAt();
                }
            }
            Envelope envelope = visible.get(group.getKey());
            ranked.add(new Ranked(envelope, score, mostRecent, suggestedAmount(groupEntries)));
        }

        ranked.sort(Comparator
                .comparingInt(Ranked::score).reversed()
                .thenComparing(Ranked::mostRecent, Comparator.reverseOrder()));

        List<SuggestionItem> items = ranked.stream()
                .limit(MAX_ITEMS)
                .map(r -> new SuggestionItem(r.envelope().id(), r.envelope().name(), r.suggestedAmount()))
                .toList();
        return new SuggestionResult(asOf, items);
    }

    private static Money suggestedAmount(List<LedgerEntry> groupEntries) {
        Map<Money, AmountStat> stats = new HashMap<>();
        for (LedgerEntry entry : groupEntries) {
            AmountStat stat = stats.computeIfAbsent(entry.amount(), ignored -> new AmountStat());
            stat.count++;
            if (entry.createdAt().isAfter(stat.mostRecent)) {
                stat.mostRecent = entry.createdAt();
            }
        }
        Money best = null;
        int bestCount = 0;
        Instant bestRecent = Instant.EPOCH;
        for (Map.Entry<Money, AmountStat> entry : stats.entrySet()) {
            AmountStat stat = entry.getValue();
            if (stat.count < MIN_AMOUNT_OCCURRENCES) {
                continue;
            }
            if (stat.count > bestCount
                    || (stat.count == bestCount && stat.mostRecent.isAfter(bestRecent))) {
                best = entry.getKey();
                bestCount = stat.count;
                bestRecent = stat.mostRecent;
            }
        }
        return best;
    }

    private static final class AmountStat {
        int count;
        Instant mostRecent = Instant.EPOCH;
    }

    private record Ranked(Envelope envelope, int score, Instant mostRecent, Money suggestedAmount) {
    }

    public record SuggestionItem(UUID envelopeId, String name, Money suggestedAmount) {
    }

    public record SuggestionResult(ZonedDateTime asOf, List<SuggestionItem> items) {
    }
}
