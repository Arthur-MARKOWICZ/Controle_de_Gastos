package br.com.controlegastos.envelopes.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.controlegastos.envelopes.domain.AllocationExceedsIncomeException;
import br.com.controlegastos.envelopes.domain.Envelope;
import br.com.controlegastos.envelopes.domain.EnvelopePurpose;
import br.com.controlegastos.envelopes.infrastructure.EnvelopeParticipantRepository;
import br.com.controlegastos.envelopes.infrastructure.EnvelopeRepository;
import br.com.controlegastos.identity.application.AuthenticationService;
import br.com.controlegastos.income.application.IncomeQuery;
import br.com.controlegastos.income.application.IncomeSnapshot;
import br.com.controlegastos.shared.money.Money;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

class EnvelopeServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-15T15:00:00Z");

    private final EnvelopeRepository envelopes = mock(EnvelopeRepository.class);
    private final EnvelopeParticipantRepository participants = mock(EnvelopeParticipantRepository.class);
    private final AuthenticationService authentication = mock(AuthenticationService.class);
    private final IncomeQuery incomes = mock(IncomeQuery.class);
    private final EnvelopeService service =
            new EnvelopeService(envelopes, participants, authentication, incomes, Clock.fixed(NOW, ZoneOffset.UTC));
    private final UUID owner = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(authentication.currentUserId()).thenReturn(owner);
        when(envelopes.save(any(Envelope.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void income(String amount) {
        when(incomes.findEffective(owner, YearMonth.of(2026, 9)))
                .thenReturn(Optional.of(new IncomeSnapshot(Money.brl(amount), YearMonth.of(2026, 1), NOW)));
    }

    private Envelope existing(String name, String base) {
        Envelope envelope = Envelope.create(owner, name, EnvelopePurpose.LIMIT, Money.brl(base), NOW.minusSeconds(60));
        when(envelopes.findById(envelope.id())).thenReturn(Optional.of(envelope));
        return envelope;
    }

    @Test
    @DisplayName("cria verba do titular autenticado dentro da renda")
    void criaVerbaDentroDaRenda() {
        income("1000.00");
        when(envelopes.findActiveByOwnerId(owner)).thenReturn(List.of());

        Envelope created = service.create("Mercado", EnvelopePurpose.LIMIT, Money.brl("400.00"), null, false);

        assertThat(created.ownerId()).isEqualTo(owner);
        assertThat(created.baseAmount()).isEqualTo(Money.brl("400.00"));
        verify(envelopes).save(created);
    }

    @Test
    @DisplayName("aceita alocação que fecha exatamente a renda, sem arredondar")
    void aceitaAlocacaoNoLimite() {
        income("1000.00");
        Envelope other = existing("Aluguel", "900.00");
        when(envelopes.findActiveByOwnerId(owner)).thenReturn(List.of(other));

        Envelope created = service.create("Lazer", EnvelopePurpose.LIMIT, Money.brl("100.00"), null, false);

        assertThat(created.baseAmount().toPlainString()).isEqualTo("100.00");
    }

    @Test
    @DisplayName("recusa alocação um centavo acima da renda com AllocationExceedsIncomeException")
    void recusaAlocacaoAcimaDaRenda() {
        income("1000.00");
        Envelope other = existing("Aluguel", "900.00");
        when(envelopes.findActiveByOwnerId(owner)).thenReturn(List.of(other));

        assertThatThrownBy(() -> service.create("Lazer", EnvelopePurpose.LIMIT, Money.brl("100.01"), null, false))
                .isInstanceOf(AllocationExceedsIncomeException.class)
                .hasMessage("A soma das verbas-base excede a renda mensal");
        verify(envelopes, never()).save(any());
    }

    @Test
    @DisplayName("valor com três casas decimais é rejeitado antes de chegar ao serviço")
    void valorComTresCasasRejeitado() {
        assertThatThrownBy(() -> Money.brl("10.555"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("O valor monetário deve ter no máximo duas casas decimais");
    }

    @Test
    @DisplayName("verba-base negativa lança IllegalArgumentException")
    void verbaNegativaFalha() {
        when(envelopes.findActiveByOwnerId(owner)).thenReturn(List.of());

        assertThatThrownBy(() -> service.create("Mercado", EnvelopePurpose.LIMIT, Money.brl("-0.01"), null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A verba-base não pode ser negativa");
    }

    @Test
    @DisplayName("terceiro sem vínculo não vê a verba: EnvelopeNotFoundException")
    void terceiroNaoVeVerba() {
        Envelope envelope = existing("Mercado", "100.00");
        when(authentication.currentUserId()).thenReturn(stranger);
        when(envelopes.isVisibleToUser(envelope.id(), stranger)).thenReturn(false);

        assertThatThrownBy(() -> service.getVisible(envelope.id())).isInstanceOf(EnvelopeNotFoundException.class);
    }

    @Test
    @DisplayName("participante vê a verba compartilhada")
    void participanteVeVerba() {
        Envelope envelope = existing("Mercado", "100.00");
        when(authentication.currentUserId()).thenReturn(stranger);
        when(envelopes.isVisibleToUser(envelope.id(), stranger)).thenReturn(true);

        assertThat(service.getVisible(envelope.id())).isSameAs(envelope);
    }

    @Test
    @DisplayName("participante não edita a verba do proprietário: EnvelopeForbiddenException")
    void participanteNaoEdita() {
        Envelope envelope = existing("Mercado", "100.00");
        when(authentication.currentUserId()).thenReturn(stranger);

        assertThatThrownBy(() -> service.update(envelope.id(), envelope.version(), "Outro", null, null, null, null))
                .isInstanceOf(EnvelopeForbiddenException.class)
                .hasMessage("Somente o proprietário pode editar a verba");
        assertThat(envelope.name()).isEqualTo("Mercado");
    }

    @Test
    @DisplayName("edição com versão desatualizada lança OptimisticLockingFailureException")
    void edicaoConcorrenteFalha() {
        Envelope envelope = existing("Mercado", "100.00");

        assertThatThrownBy(() -> service.update(envelope.id(), envelope.version() + 1, "Outro", null, null, null, null))
                .isInstanceOf(OptimisticLockingFailureException.class);
        verify(envelopes, never()).save(any());
    }

    @Test
    @DisplayName("editar verba arquivada lança EnvelopeNotFoundException")
    void editarArquivadaFalha() {
        Envelope envelope = existing("Mercado", "100.00");
        envelope.archive(NOW);

        assertThatThrownBy(() -> service.update(envelope.id(), envelope.version(), "Outro", null, null, null, null))
                .isInstanceOf(EnvelopeNotFoundException.class);
    }

    @Test
    @DisplayName("editar ignora a própria verba ao somar as alocações")
    void edicaoIgnoraPropriaVerba() {
        income("1000.00");
        Envelope envelope = existing("Mercado", "900.00");
        when(envelopes.findActiveByOwnerId(owner)).thenReturn(List.of(envelope));

        Envelope updated = service.update(envelope.id(), envelope.version(), null, null, Money.brl("1000.00"), null, null);

        assertThat(updated.baseAmount()).isEqualTo(Money.brl("1000.00"));
    }

    @Test
    @DisplayName("participante não arquiva a verba: EnvelopeForbiddenException")
    void participanteNaoArquiva() {
        Envelope envelope = existing("Mercado", "100.00");
        when(authentication.currentUserId()).thenReturn(stranger);

        assertThatThrownBy(() -> service.archive(envelope.id())).isInstanceOf(EnvelopeForbiddenException.class);
        assertThat(envelope.isArchived()).isFalse();
    }

    @Test
    @DisplayName("arquivar duas vezes é idempotente")
    void arquivarDuasVezesIdempotente() {
        Envelope envelope = existing("Mercado", "100.00");
        service.archive(envelope.id());
        Instant first = envelope.archivedAt();

        service.archive(envelope.id());

        assertThat(envelope.archivedAt()).isEqualTo(first);
    }

    @Test
    @DisplayName("remover quem não participa lança EnvelopeNotFoundException")
    void removerNaoParticipanteFalha() {
        UUID envelopeId = UUID.randomUUID();
        when(participants.existsByEnvelopeIdAndUserId(envelopeId, stranger)).thenReturn(false);

        assertThatThrownBy(() -> service.removeParticipant(envelopeId, stranger))
                .isInstanceOf(EnvelopeNotFoundException.class);
        verify(participants, never()).deleteByEnvelopeIdAndUserId(any(), any());
    }

    @Test
    @DisplayName("verba inexistente lança EnvelopeNotFoundException")
    void verbaInexistenteFalha() {
        UUID ghost = UUID.randomUUID();
        when(envelopes.findById(ghost)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getVisible(ghost)).isInstanceOf(EnvelopeNotFoundException.class);
        assertThatThrownBy(() -> service.requireOwned(ghost)).isInstanceOf(EnvelopeNotFoundException.class);
    }
}
