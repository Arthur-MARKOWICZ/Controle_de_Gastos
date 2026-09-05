package br.com.controlegastos.identity.application;

import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.TimeProvider;
import java.time.Clock;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TotpService {

    private static final String ISSUER = "Controle de Gastos";
    private static final int DIGITS = 6;
    private static final int PERIOD_SECONDS = 30;
    private static final int ALLOWED_TIME_PERIOD_DISCREPANCY = 1;

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final QrGenerator qrGenerator = new ZxingPngQrGenerator();
    private final CodeVerifier codeVerifier;

    public TotpService(Clock clock) {
        TimeProvider timeProvider = () -> clock.instant().getEpochSecond();
        CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1, DIGITS);
        DefaultCodeVerifier verifier = new DefaultCodeVerifier(codeGenerator, timeProvider);
        verifier.setTimePeriod(PERIOD_SECONDS);
        verifier.setAllowedTimePeriodDiscrepancy(ALLOWED_TIME_PERIOD_DISCREPANCY);
        this.codeVerifier = verifier;
    }

    public boolean verifyCode(String base32Secret, String code) {
        return code != null && !code.isBlank() && codeVerifier.isValidCode(base32Secret, code);
    }

    public EnrollmentMaterial generateEnrollmentMaterial(UUID userId) {
        String secret = secretGenerator.generate();
        QrData data = new QrData.Builder()
                .label(userId.toString())
                .secret(secret)
                .issuer(ISSUER)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(DIGITS)
                .period(PERIOD_SECONDS)
                .build();
        String qrImageDataUri = "data:image/png;base64," + Base64.getEncoder().encodeToString(generateQrPng(data));
        return new EnrollmentMaterial(secret, data.getUri(), qrImageDataUri);
    }

    private byte[] generateQrPng(QrData data) {
        try {
            return qrGenerator.generate(data);
        } catch (QrGenerationException exception) {
            throw new IllegalStateException("Falha ao gerar QR Code de MFA", exception);
        }
    }

    public record EnrollmentMaterial(String secret, String otpauthUri, String qrImageDataUri) {
    }
}
