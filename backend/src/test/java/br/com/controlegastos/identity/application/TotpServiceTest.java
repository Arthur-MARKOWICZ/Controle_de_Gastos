package br.com.controlegastos.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.CodeGenerationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TotpServiceTest {

    private static final Instant START = Instant.parse("2026-09-05T12:00:00Z");
    private static final CodeGenerator CODE_GENERATOR = new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6);

    @Test
    void aCodeValidAtItsOwnWindowStaysValidOneWindowBeforeAndAfter() {
        MutableClock clock = new MutableClock(START);
        TotpService service = new TotpService(clock);
        String secret = service.generateEnrollmentMaterial(UUID.randomUUID()).secret();
        String code = codeAt(secret, START);

        assertThat(service.verifyCode(secret, code)).isTrue();

        clock.set(START.minusSeconds(30));
        assertThat(service.verifyCode(secret, code)).isTrue();

        clock.set(START.plusSeconds(30));
        assertThat(service.verifyCode(secret, code)).isTrue();
    }

    @Test
    void aCodeIsRejectedOutsideTheAllowedTolerance() {
        MutableClock clock = new MutableClock(START);
        TotpService service = new TotpService(clock);
        String secret = service.generateEnrollmentMaterial(UUID.randomUUID()).secret();
        String code = codeAt(secret, START);

        clock.set(START.minusSeconds(60));
        assertThat(service.verifyCode(secret, code)).isFalse();

        clock.set(START.plusSeconds(60));
        assertThat(service.verifyCode(secret, code)).isFalse();
    }

    @Test
    void aNullOrGarbageCodeIsNeverValid() {
        TotpService service = new TotpService(new MutableClock(START));
        String secret = service.generateEnrollmentMaterial(UUID.randomUUID()).secret();

        assertThat(service.verifyCode(secret, null)).isFalse();
        assertThat(service.verifyCode(secret, "000000")).isFalse();
    }

    @Test
    void theOtpauthUriUsesTheUserIdIssuerPeriodAndDigitsButNeverTheEmail() {
        TotpService service = new TotpService(new MutableClock(START));
        UUID userId = UUID.randomUUID();

        TotpService.EnrollmentMaterial material = service.generateEnrollmentMaterial(userId);

        assertThat(material.otpauthUri()).startsWith("otpauth://totp/");
        assertThat(material.otpauthUri()).contains(userId.toString());
        assertThat(material.otpauthUri()).containsIgnoringCase("Controle");
        assertThat(material.otpauthUri()).contains("period=30");
        assertThat(material.otpauthUri()).contains("digits=6");
        assertThat(material.otpauthUri()).doesNotContain("@");
    }

    @Test
    void theQrImageIsAPngDataUri() {
        TotpService service = new TotpService(new MutableClock(START));

        TotpService.EnrollmentMaterial material = service.generateEnrollmentMaterial(UUID.randomUUID());

        assertThat(material.qrImageDataUri()).startsWith("data:image/png;base64,");
    }

    private static String codeAt(String secret, Instant instant) {
        try {
            long counter = Math.floorDiv(instant.getEpochSecond(), 30);
            return CODE_GENERATOR.generate(secret, counter);
        } catch (CodeGenerationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
