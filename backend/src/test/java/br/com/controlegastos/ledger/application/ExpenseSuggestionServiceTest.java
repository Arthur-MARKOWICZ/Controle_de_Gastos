package br.com.controlegastos.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import br.com.controlegastos.envelopes.application.EnvelopeService;
import br.com.controlegastos.envelopes.domain.Envelope;
import br.com.controlegastos.envelopes.domain.EnvelopePurpose;
import br.com.controlegastos.identity.application.AuthenticationService;
import br.com.controlegastos.ledger.domain.LedgerEntry;
import br.com.controlegastos.ledger.infrastructure.LedgerEntryRepository;
import br.com.controlegastos.shared.money.Money;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpenseSuggestionServiceTest {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");
    /** Terça 2026-11-24 12:30 America/Sao_Paulo. */
    private static final Instant NOW = Instant.parse("2026-11-24T15:30:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock EnvelopeService envelopes;
    @Mock LedgerEntryRepository entries;
    @Mock AuthenticationService authentication;

    private final UUID owner = UUID.randomUUID();
    private final UUID participant = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();

    private ExpenseSuggestionService service() {
        return new ExpenseSuggestionService(envelopes, entries, authentication, CLOCK);
    }

    private Envelope limit(UUID ownerId, String name) {
        return Envelope.create(ownerId, name, EnvelopePurpose.LIMIT, Money.brl("500.00"), NOW.minusSeconds(86_400L * 120));
    }

    private LedgerEntry expense(Envelope envelope, UUID author, String amount, LocalDate occurredAt, Instant createdAt) {
        return LedgerEntry.expense(envelope.id(), envelope.ownerId(), author, Money.brl(amount), occurredAt, null, createdAt);
    }

    /** Instant no fuso SP na data/hora indicadas. */
    private static Instant atSp(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute).atZone(SP).toInstant();
    }

    @Test
    @DisplayName("titular com padrão de terça ao meio-dia ranqueia a verba e sugere a moda")
    void ownerWithWeekdayPatternRanksEnvelopeAndSuggestsMode() {
        Envelope almoco = limit(owner, "Almoço");
        when(authentication.currentUserId()).thenReturn(owner);
        when(envelopes.listVisible()).thenReturn(List.of(almoco));

        LocalDate tue1 = LocalDate.of(2026, 11, 10);
        LocalDate tue2 = LocalDate.of(2026, 11, 17);
        LocalDate tue3 = LocalDate.of(2026, 11, 3);
        when(entries.findActiveExpensesByAuthorBetween(eq(owner), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(
                        expense(almoco, owner, "32.50", tue1, atSp(tue1, 12, 10)),
                        expense(almoco, owner, "32.50", tue2, atSp(tue2, 12, 20)),
                        expense(almoco, owner, "32.50", tue3, atSp(tue3, 12, 5)),
                        expense(almoco, owner, "40.00", LocalDate.of(2026, 11, 5), atSp(LocalDate.of(2026, 11, 5), 19, 0))
                ));

        ExpenseSuggestionService.SuggestionResult result = service().suggest();

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().envelopeId()).isEqualTo(almoco.id());
        assertThat(result.items().getFirst().name()).isEqualTo("Almoço");
        assertThat(result.items().getFirst().suggestedAmount()).isEqualTo(Money.brl("32.50"));
        assertThat(result.asOf().getZone()).isEqualTo(SP);
    }

    @Test
    @DisplayName("mesmo DOW e bucket de hora pesa mais que outra verba no mesmo horário")
    void sameDowAndHourBucketBeatsOtherEnvelope() {
        Envelope uber = limit(owner, "Uber");
        Envelope mercado = limit(owner, "Mercado");
        when(authentication.currentUserId()).thenReturn(owner);
        when(envelopes.listVisible()).thenReturn(List.of(uber, mercado));

        // Consulta: sexta 18:30 SP — NOW é terça; usar sexta fixa via clock override no serviço
        Instant fridayEvening = Instant.parse("2026-11-27T21:30:00Z"); // sexta 18:30 SP
        Clock fridayClock = Clock.fixed(fridayEvening, ZoneOffset.UTC);
        ExpenseSuggestionService fridayService =
                new ExpenseSuggestionService(envelopes, entries, authentication, fridayClock);

        List<LedgerEntry> history = new java.util.ArrayList<>();
        for (int week = 0; week < 5; week++) {
            LocalDate friday = LocalDate.of(2026, 10, 30).plusWeeks(week);
            history.add(expense(uber, owner, "22.00", friday, atSp(friday, 18, 15)));
        }
        LocalDate friA = LocalDate.of(2026, 11, 6);
        LocalDate friB = LocalDate.of(2026, 11, 13);
        history.add(expense(mercado, owner, "80.00", friA, atSp(friA, 18, 40)));
        history.add(expense(mercado, owner, "80.00", friB, atSp(friB, 18, 45)));
        when(entries.findActiveExpensesByAuthorBetween(eq(owner), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(history);

        List<ExpenseSuggestionService.SuggestionItem> items = fridayService.suggest().items();

        assertThat(items).hasSize(2);
        assertThat(items.getFirst().name()).isEqualTo("Uber");
        assertThat(items.get(1).name()).isEqualTo("Mercado");
    }

    @Test
    @DisplayName("histórico só de manhã ainda ranqueia pelo DOW à noite")
    void fallbackWhenNoHourMatchStillRanksByDow() {
        Envelope cafe = limit(owner, "Café");
        when(authentication.currentUserId()).thenReturn(owner);
        when(envelopes.listVisible()).thenReturn(List.of(cafe));

        Instant mondayNight = Instant.parse("2026-11-23T23:00:00Z"); // segunda 20:00 SP
        Clock mondayNightClock = Clock.fixed(mondayNight, ZoneOffset.UTC);
        ExpenseSuggestionService nightService =
                new ExpenseSuggestionService(envelopes, entries, authentication, mondayNightClock);

        LocalDate mon1 = LocalDate.of(2026, 11, 9);
        LocalDate mon2 = LocalDate.of(2026, 11, 16);
        when(entries.findActiveExpensesByAuthorBetween(eq(owner), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(
                        expense(cafe, owner, "8.00", mon1, atSp(mon1, 8, 30)),
                        expense(cafe, owner, "8.00", mon2, atSp(mon2, 9, 0))
                ));

        assertThat(nightService.suggest().items()).extracting(ExpenseSuggestionService.SuggestionItem::name)
                .containsExactly("Café");
    }

    @Test
    @DisplayName("verba com menos de 2 gastos na janela não entra")
    void envelopeBelowMinimumCountIsExcluded() {
        Envelope farmacia = limit(owner, "Farmácia");
        when(authentication.currentUserId()).thenReturn(owner);
        when(envelopes.listVisible()).thenReturn(List.of(farmacia));
        when(entries.findActiveExpensesByAuthorBetween(eq(owner), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(
                        expense(farmacia, owner, "45.00", LocalDate.of(2026, 11, 20), atSp(LocalDate.of(2026, 11, 20), 10, 0))
                ));

        assertThat(service().suggest().items()).isEmpty();
    }

    @Test
    @DisplayName("verba arquivada fora de listVisible não aparece mesmo com histórico dominante")
    void archivedEnvelopeExcluded() {
        Envelope viagem = limit(owner, "Viagem");
        Envelope almoco = limit(owner, "Almoço");
        when(authentication.currentUserId()).thenReturn(owner);
        when(envelopes.listVisible()).thenReturn(List.of(almoco));

        LocalDate d1 = LocalDate.of(2026, 11, 10);
        LocalDate d2 = LocalDate.of(2026, 11, 17);
        when(entries.findActiveExpensesByAuthorBetween(eq(owner), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(
                        expense(viagem, owner, "200.00", d1, atSp(d1, 12, 0)),
                        expense(viagem, owner, "200.00", d2, atSp(d2, 12, 0)),
                        expense(viagem, owner, "200.00", LocalDate.of(2026, 11, 3), atSp(LocalDate.of(2026, 11, 3), 12, 0)),
                        expense(almoco, owner, "32.50", d1, atSp(d1, 12, 10)),
                        expense(almoco, owner, "32.50", d2, atSp(d2, 12, 20))
                ));

        assertThat(service().suggest().items()).extracting(ExpenseSuggestionService.SuggestionItem::name)
                .containsExactly("Almoço");
    }

    @Test
    @DisplayName("participante só vê a verba compartilhada em que pode gastar")
    void participantOnlySeesSpendableSharedEnvelope() {
        Envelope casa = limit(owner, "Casa");
        when(authentication.currentUserId()).thenReturn(participant);
        when(envelopes.listVisible()).thenReturn(List.of(casa));

        LocalDate d1 = LocalDate.of(2026, 11, 10);
        LocalDate d2 = LocalDate.of(2026, 11, 17);
        when(entries.findActiveExpensesByAuthorBetween(eq(participant), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(
                        expense(casa, participant, "50.00", d1, atSp(d1, 12, 0)),
                        expense(casa, participant, "50.00", d2, atSp(d2, 12, 0))
                ));

        assertThat(service().suggest().items()).extracting(ExpenseSuggestionService.SuggestionItem::name)
                .containsExactly("Casa");
    }

    @Test
    @DisplayName("terceiro sem verbas recebe lista vazia")
    void thirdPartyGetsEmptyList() {
        when(authentication.currentUserId()).thenReturn(stranger);
        when(envelopes.listVisible()).thenReturn(List.of());

        assertThat(service().suggest().items()).isEmpty();
    }

    @Test
    @DisplayName("moda do valor: empate pelo mais recente; abaixo de 2 ocorrências do mesmo valor → null")
    void suggestedAmountModeTieAndInsufficient() {
        Envelope cafe = limit(owner, "Café");
        Envelope padaria = limit(owner, "Padaria");
        Envelope farmacia = limit(owner, "Farmácia");
        when(authentication.currentUserId()).thenReturn(owner);
        when(envelopes.listVisible()).thenReturn(List.of(cafe, padaria, farmacia));

        LocalDate d1 = LocalDate.of(2026, 10, 1);
        LocalDate d2 = LocalDate.of(2026, 10, 8);
        LocalDate d3 = LocalDate.of(2026, 10, 15);
        LocalDate d4 = LocalDate.of(2026, 10, 22);
        LocalDate p1 = LocalDate.of(2026, 10, 2);
        LocalDate p2 = LocalDate.of(2026, 10, 9);
        LocalDate p3 = LocalDate.of(2026, 10, 16);
        LocalDate p4 = LocalDate.of(2026, 10, 23);
        LocalDate f1 = LocalDate.of(2026, 11, 1);
        LocalDate f2 = LocalDate.of(2026, 11, 8);

        when(entries.findActiveExpensesByAuthorBetween(eq(owner), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(
                        expense(cafe, owner, "8.00", d1, atSp(d1, 8, 0)),
                        expense(cafe, owner, "8.00", d2, atSp(d2, 8, 0)),
                        expense(cafe, owner, "8.00", d3, atSp(d3, 8, 0)),
                        expense(cafe, owner, "12.00", d4, atSp(d4, 8, 0)),
                        expense(padaria, owner, "10.00", p1, atSp(p1, 9, 0)),
                        expense(padaria, owner, "10.00", p2, atSp(p2, 9, 0)),
                        expense(padaria, owner, "15.00", p3, atSp(p3, 9, 0)),
                        expense(padaria, owner, "15.00", p4, atSp(p4, 9, 0)),
                        expense(farmacia, owner, "20.00", f1, atSp(f1, 10, 0)),
                        expense(farmacia, owner, "35.00", f2, atSp(f2, 10, 0))
                ));

        var byName = new java.util.HashMap<String, ExpenseSuggestionService.SuggestionItem>();
        for (var item : service().suggest().items()) {
            byName.put(item.name(), item);
        }

        assertThat(byName.get("Café").suggestedAmount()).isEqualTo(Money.brl("8.00"));
        assertThat(byName.get("Padaria").suggestedAmount()).isEqualTo(Money.brl("15.00"));
        assertThat(byName.get("Farmácia").suggestedAmount()).isNull();
    }
}
