package br.com.controlegastos.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "auth_attempt")
public class AuthAttempt {

    @Id
    @Column(name = "attempt_key", length = 64)
    private String key;

    @Column(name = "attempt_count", nullable = false)
    private int count;

    @Column(name = "window_started_at", nullable = false)
    private Instant windowStartedAt;

    @Column(name = "blocked_until")
    private Instant blockedUntil;

    @Column(name = "expires_at", nullable = false)
    Instant expiresAt;

    protected AuthAttempt() {
    }

    public AuthAttempt(String key, Instant now, Duration retention) {
        this.key = key;
        this.windowStartedAt = now;
        this.expiresAt = now.plus(retention);
    }

    public boolean isBlockedAt(Instant now) {
        return blockedUntil != null && now.isBefore(blockedUntil);
    }

    public void recordFailure(Instant now, Duration window, Duration blockDuration, Duration retention, int limit) {
        if (!now.isBefore(windowStartedAt.plus(window))) {
            count = 0;
            windowStartedAt = now;
            blockedUntil = null;
        }
        count++;
        if (count >= limit) {
            blockedUntil = now.plus(blockDuration);
        }
        expiresAt = now.plus(retention);
    }
}
