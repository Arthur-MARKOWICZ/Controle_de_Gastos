package br.com.controlegastos.identity.web;

import br.com.controlegastos.identity.domain.UserAccount;
import br.com.controlegastos.identity.application.UserAccountService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserAccountService accounts;

    public UserController(UserAccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/me")
    CurrentUserResponse currentUser() {
        UserAccount user = accounts.currentAccount();
        return new CurrentUserResponse(
                user.id(),
                user.emailNormalized(),
                user.emailVerifiedAt() != null,
                user.createdAt(),
                user.updatedAt()
        );
    }

    record CurrentUserResponse(
            UUID id,
            String email,
            boolean emailVerified,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
