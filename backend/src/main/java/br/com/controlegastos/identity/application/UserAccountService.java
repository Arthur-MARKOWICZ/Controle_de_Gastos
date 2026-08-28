package br.com.controlegastos.identity.application;

import br.com.controlegastos.identity.domain.UserAccount;
import br.com.controlegastos.identity.infrastructure.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccountService {

    private final UserAccountRepository users;
    private final AuthenticationService authentication;

    public UserAccountService(UserAccountRepository users, AuthenticationService authentication) {
        this.users = users;
        this.authentication = authentication;
    }

    @Transactional(readOnly = true)
    public UserAccount currentAccount() {
        return users.findById(authentication.currentUserId())
                .orElseThrow(InvalidCredentialsException::new);
    }
}
