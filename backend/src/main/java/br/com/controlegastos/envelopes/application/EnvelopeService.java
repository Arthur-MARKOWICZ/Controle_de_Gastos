package br.com.controlegastos.envelopes.application;

import br.com.controlegastos.envelopes.domain.AllocationExceedsIncomeException;
import br.com.controlegastos.envelopes.domain.AnnualExpenseFundingMode;
import br.com.controlegastos.envelopes.domain.BaseAllocation;
import br.com.controlegastos.envelopes.domain.Envelope;
import br.com.controlegastos.envelopes.domain.EnvelopeParticipant;
import br.com.controlegastos.envelopes.domain.EnvelopePurpose;
import br.com.controlegastos.envelopes.domain.MonthlyAllocationPlan;
import br.com.controlegastos.envelopes.domain.MonthlyCommittedAllocation;
import br.com.controlegastos.envelopes.infrastructure.EnvelopeParticipantRepository;
import br.com.controlegastos.envelopes.infrastructure.EnvelopeRepository;
import br.com.controlegastos.identity.application.AuthenticationService;
import br.com.controlegastos.income.application.IncomeQuery;
import br.com.controlegastos.shared.money.Money;
import java.time.Clock;
import java.time.Instant;
import java.time.MonthDay;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.modulith.NamedInterface;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@NamedInterface
public class EnvelopeService {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Sao_Paulo");

    private final EnvelopeRepository envelopes;
    private final EnvelopeParticipantRepository participants;
    private final AuthenticationService authentication;
    private final IncomeQuery incomes;
    private final Clock clock;

    public EnvelopeService(EnvelopeRepository envelopes,
                           EnvelopeParticipantRepository participants,
                           AuthenticationService authentication,
                           IncomeQuery incomes,
                           Clock clock) {
        this.envelopes = envelopes;
        this.participants = participants;
        this.authentication = authentication;
        this.incomes = incomes;
        this.clock = clock;
    }

    @Transactional
    public Envelope create(String name, EnvelopePurpose purpose, Money baseAmount, Money targetAmount) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(purpose);
        Objects.requireNonNull(baseAmount);
        Instant now = clock.instant();
        UUID ownerId = authentication.currentUserId();
        YearMonth month = YearMonth.now(clock.withZone(BUSINESS_ZONE));
        ensureFitsIncome(ownerId, month, null, MonthlyCommittedAllocation.amountFor(
                Envelope.create(ownerId, name, purpose, baseAmount, targetAmount, now), month));
        Envelope envelope = Envelope.create(ownerId, name, purpose, baseAmount, targetAmount, now);
        return envelopes.save(envelope);
    }

    @Transactional
    public Envelope createAnnualExpense(String name, Money annualAmount, MonthDay dueDate, AnnualExpenseFundingMode fundingMode) {
        UUID ownerId = authentication.currentUserId();
        Instant now = clock.instant();
        YearMonth month = YearMonth.now(clock.withZone(BUSINESS_ZONE));
        Envelope candidate = Envelope.createAnnualExpense(ownerId, name, annualAmount, dueDate, fundingMode, now);
        ensureFitsIncome(ownerId, month, null, MonthlyCommittedAllocation.amountFor(candidate, month));
        return envelopes.save(candidate);
    }

    @Transactional(readOnly = true)
    public List<Envelope> listVisible() {
        UUID userId = authentication.currentUserId();
        return envelopes.findVisibleByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<Envelope> listVisibleIncludingArchived() {
        return envelopes.findVisibleIncludingArchivedByUserId(authentication.currentUserId());
    }

    @Transactional(readOnly = true)
    public Envelope getVisible(UUID envelopeId) {
        UUID userId = authentication.currentUserId();
        Envelope envelope = envelopes.findById(envelopeId)
                .orElseThrow(() -> new EnvelopeNotFoundException(envelopeId));
        boolean visible = envelopes.isVisibleToUser(envelopeId, userId);
        if (!visible) {
            // fallback to owner check for cases where participant table not yet used but envelope owner is user
            if (!envelope.ownerId().equals(userId)) {
                throw new EnvelopeNotFoundException(envelopeId);
            }
        }
        if (envelope.isArchived()) {
            throw new EnvelopeNotFoundException(envelopeId);
        }
        return envelope;
    }

    @Transactional
    public Envelope update(UUID envelopeId, long expectedVersion, String newName, EnvelopePurpose newPurpose,
                           Money newBaseAmount, Money newTargetAmount) {
        UUID userId = authentication.currentUserId();
        Envelope envelope = envelopes.findById(envelopeId)
                .orElseThrow(() -> new EnvelopeNotFoundException(envelopeId));
        if (!envelope.ownerId().equals(userId)) {
            throw new EnvelopeForbiddenException("Somente o proprietário pode editar a verba");
        }
        if (envelope.isArchived()) {
            throw new EnvelopeNotFoundException(envelopeId);
        }
        requireVersion(envelope, expectedVersion);
        if (newName != null) envelope.rename(newName);
        if (newPurpose != null || newBaseAmount != null || newTargetAmount != null) {
            EnvelopePurpose effectivePurpose = newPurpose == null ? envelope.purpose() : newPurpose;
            envelope.changeFinancialConfiguration(
                    effectivePurpose,
                    newBaseAmount == null ? envelope.baseAmount() : newBaseAmount,
                    effectivePurpose == EnvelopePurpose.SAVINGS_TARGET
                            ? (newTargetAmount == null ? envelope.targetAmount() : newTargetAmount)
                            : null);
        }
        YearMonth month = YearMonth.now(clock.withZone(BUSINESS_ZONE));
        ensureFitsIncome(userId, month, envelope.id(), MonthlyCommittedAllocation.amountFor(envelope, month));
        return envelopes.save(envelope);
    }

    @Transactional
    public Envelope updateAnnualExpense(UUID envelopeId, long expectedVersion, String newName, Money annualAmount,
                                        MonthDay dueDate, AnnualExpenseFundingMode fundingMode) {
        Envelope envelope = envelopes.findById(envelopeId).orElseThrow(() -> new EnvelopeNotFoundException(envelopeId));
        if (!envelope.isOwnedBy(authentication.currentUserId())) {
            throw new EnvelopeForbiddenException("Somente o proprietário pode editar a verba");
        }
        if (envelope.isArchived() || !envelope.isAnnualExpense()) {
            throw new EnvelopeNotFoundException(envelopeId);
        }
        requireVersion(envelope, expectedVersion);
        if (newName != null) envelope.rename(newName);
        if (annualAmount != null || dueDate != null || fundingMode != null) {
            envelope.changeAnnualExpense(annualAmount == null ? envelope.annualAmount() : annualAmount,
                    dueDate == null ? envelope.annualDueDate() : dueDate,
                    fundingMode == null ? envelope.annualFundingMode() : fundingMode);
        }
        YearMonth month = YearMonth.now(clock.withZone(BUSINESS_ZONE));
        ensureFitsIncome(envelope.ownerId(), month, envelope.id(), MonthlyCommittedAllocation.amountFor(envelope, month));
        return envelopes.save(envelope);
    }

    @Transactional
    public void archive(UUID envelopeId) {
        UUID userId = authentication.currentUserId();
        Envelope envelope = envelopes.findById(envelopeId)
                .orElseThrow(() -> new EnvelopeNotFoundException(envelopeId));
        if (!envelope.ownerId().equals(userId)) {
            throw new EnvelopeForbiddenException("Somente o proprietário pode arquivar a verba");
        }
        if (envelope.isArchived()) return;
        envelope.archive(clock.instant());
        envelopes.save(envelope);
    }

    /** Garante que o usuário corrente é o proprietário da verba ativa. */
    @Transactional(readOnly = true)
    public Envelope requireOwned(UUID envelopeId) {
        UUID userId = authentication.currentUserId();
        Envelope envelope = envelopes.findById(envelopeId)
                .orElseThrow(() -> new EnvelopeNotFoundException(envelopeId));
        if (envelope.isArchived()) {
            throw new EnvelopeNotFoundException(envelopeId);
        }
        if (!envelope.ownerId().equals(userId)) {
            throw new EnvelopeForbiddenException("Somente o proprietário pode gerenciar o compartilhamento");
        }
        return envelope;
    }

    @Transactional(readOnly = true)
    public boolean isParticipant(UUID envelopeId, UUID userId) {
        return participants.existsByEnvelopeIdAndUserId(envelopeId, userId);
    }

    @Transactional(readOnly = true)
    public List<EnvelopeParticipant> listParticipants(UUID envelopeId) {
        return participants.findByEnvelopeIdOrderByAddedAtAsc(envelopeId);
    }

    @Transactional
    public EnvelopeParticipant addParticipant(UUID envelopeId, UUID userId, UUID addedBy, Instant now) {
        EnvelopeParticipant row = new EnvelopeParticipant(envelopeId, userId, now, addedBy);
        return participants.save(row);
    }

    @Transactional
    public void removeParticipant(UUID envelopeId, UUID userId) {
        if (!participants.existsByEnvelopeIdAndUserId(envelopeId, userId)) {
            throw new EnvelopeNotFoundException(envelopeId);
        }
        participants.deleteByEnvelopeIdAndUserId(envelopeId, userId);
    }

    private void ensureFitsIncome(UUID ownerId, YearMonth month, UUID replacingEnvelopeId, Money candidateAmount) {
        var incomeOpt = incomes.findEffective(ownerId, month);
        if (incomeOpt.isEmpty()) {
            return;
        }
        Money income = incomeOpt.get().amount();
        List<BaseAllocation> allocations = new ArrayList<>();
        for (Envelope existing : envelopes.findActiveByOwnerId(ownerId)) {
            if (replacingEnvelopeId != null && existing.id().equals(replacingEnvelopeId)) {
                continue;
            }
            allocations.add(new BaseAllocation(existing.id().toString(), MonthlyCommittedAllocation.amountFor(existing, month)));
        }
        allocations.add(new BaseAllocation("candidate", candidateAmount));
        MonthlyAllocationPlan.create(income, allocations);
    }

    private static void requireVersion(Envelope envelope, long expectedVersion) {
        if (envelope.version() != expectedVersion) {
            throw new OptimisticLockingFailureException("A verba foi alterada em outra requisição");
        }
    }
}
