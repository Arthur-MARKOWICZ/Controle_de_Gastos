package br.com.controlegastos.identity.web;

import br.com.controlegastos.identity.application.AuthenticationService;
import br.com.controlegastos.identity.application.MfaEnrollmentService;
import br.com.controlegastos.identity.domain.TotpCredentialStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mfa")
public class MfaController {

    private final AuthenticationService authentication;
    private final MfaEnrollmentService enrollment;

    public MfaController(AuthenticationService authentication, MfaEnrollmentService enrollment) {
        this.authentication = authentication;
        this.enrollment = enrollment;
    }

    @PostMapping("/enroll")
    ResponseEntity<EnrollmentStartResponse> enroll(@Valid @RequestBody PasswordConfirmationRequest request,
                                                    HttpServletRequest httpRequest) {
        MfaEnrollmentService.EnrollmentStart start = enrollment.start(
                authentication.currentUserId(), request.password(), httpRequest.getRemoteAddr(),
                authentication.isRestrictedMfaSession());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new EnrollmentStartResponse(
                        start.otpauthUri(), start.qrImageDataUri(), start.manualEntryKey(), start.pendingExpiresAt()));
    }

    @PostMapping("/enroll/confirm")
    RecoveryCodesResponse confirm(@Valid @RequestBody CodeConfirmationRequest request, HttpServletRequest httpRequest) {
        List<String> codes = enrollment.confirm(
                authentication.currentUserId(), request.code(), httpRequest.getRemoteAddr());
        return new RecoveryCodesResponse(codes);
    }

    @PostMapping("/disable")
    ResponseEntity<Void> disable(@Valid @RequestBody PasswordConfirmationRequest request) {
        enrollment.disable(authentication.currentUserId(), request.password());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/recovery-codes")
    RecoveryCodesResponse regenerateRecoveryCodes(@Valid @RequestBody PasswordConfirmationRequest request) {
        List<String> codes = enrollment.regenerateRecoveryCodes(authentication.currentUserId(), request.password());
        return new RecoveryCodesResponse(codes);
    }

    @GetMapping("/status")
    Map<String, Object> status() {
        MfaEnrollmentService.MfaStatus status = enrollment.status(authentication.currentUserId());
        return statusBody(status.status(), status.pendingExpiresAt());
    }

    private Map<String, Object> statusBody(TotpCredentialStatus status, Instant pendingExpiresAt) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status);
        body.put("pendingExpiresAt", pendingExpiresAt);
        return body;
    }

    public record PasswordConfirmationRequest(@NotBlank String password) {
    }

    public record CodeConfirmationRequest(@NotBlank @Size(max = 10) String code) {
    }

    public record EnrollmentStartResponse(
            String otpauthUri, String qrImageDataUri, String manualEntryKey, Instant pendingExpiresAt) {
    }

    public record RecoveryCodesResponse(List<String> recoveryCodes) {
    }
}
