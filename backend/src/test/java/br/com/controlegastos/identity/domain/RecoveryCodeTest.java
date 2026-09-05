package br.com.controlegastos.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecoveryCodeTest {

    private static final Instant NOW = Instant.parse("2026-09-05T12:00:00Z");

    @Test
    void canBeConsumedOnlyOnce() {
        RecoveryCode code = RecoveryCode.issue(UUID.randomUUID(), "hash", NOW);

        assertThat(code.canBeConsumedAt(NOW)).isTrue();
        code.consume(NOW.plusSeconds(1));
        assertThat(code.canBeConsumedAt(NOW.plusSeconds(2))).isFalse();
        assertThatThrownBy(() -> code.consume(NOW.plusSeconds(2))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void invalidatingAnUnusedCodePreventsFutureConsumption() {
        RecoveryCode code = RecoveryCode.issue(UUID.randomUUID(), "hash", NOW);

        code.invalidate(NOW.plusSeconds(1));

        assertThat(code.canBeConsumedAt(NOW.plusSeconds(2))).isFalse();
        assertThat(code.invalidatedAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    void invalidatingAnAlreadyConsumedCodeDoesNothing() {
        RecoveryCode code = RecoveryCode.issue(UUID.randomUUID(), "hash", NOW);
        code.consume(NOW.plusSeconds(1));

        code.invalidate(NOW.plusSeconds(2));

        assertThat(code.invalidatedAt()).isNull();
    }
}
