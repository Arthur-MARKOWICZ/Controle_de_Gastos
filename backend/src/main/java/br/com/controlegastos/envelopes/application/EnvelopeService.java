package br.com.controlegastos.envelopes.application;

import br.com.controlegastos.envelopes.domain.Envelope;
import br.com.controlegastos.envelopes.domain.EnvelopePurpose;
import br.com.controlegastos.envelopes.infrastructure.EnvelopeRepository;
import br.com.controlegastos.identity.application.AuthenticationService;
import br.com.controlegastos.shared.money.Money;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.modulith.NamedInterface;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@NamedInterface
public class EnvelopeService {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Sao_Paulo");

    private final EnvelopeRepository envelopes;
    private final AuthenticationService authentication;
    private final Clock clock;

    public EnvelopeService(EnvelopeRepository envelopes,
                           AuthenticationService authentication,
                           Clock clock) {
        this.envelopes = envelopes;
        this.authentication = authentication;
        this.clock = clock;
    }

    @Transactional
    public Envelope create(String name, EnvelopePurpose purpose, Money baseAmount, Money targetAmount) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(purpose);
        Objects.requireNonNull(baseAmount);
        Instant now = clock.instant();
        UUID ownerId = authentication.currentUserId();
        // Validação de renda delegada ao IncomeService via IncomeChangeConstraint (evita dependência direta em MonthlyIncomeRepository)
        Envelope envelope = Envelope.create(ownerId, name, purpose, baseAmount, targetAmount, now);
        return envelopes.save(envelope);
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
    public Envelope update(UUID envelopeId, String newName, EnvelopePurpose newPurpose, Money newBaseAmount, Money newTargetAmount) {
        UUID userId = authentication.currentUserId();
        Envelope envelope = envelopes.findById(envelopeId)
                .orElseThrow(() -> new EnvelopeNotFoundException(envelopeId));
        if (!envelope.ownerId().equals(userId)) {
            throw new EnvelopeForbiddenException("Somente o proprietário pode editar a verba");
        }
        if (envelope.isArchived()) {
            throw new EnvelopeNotFoundException(envelopeId);
        }
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
}
