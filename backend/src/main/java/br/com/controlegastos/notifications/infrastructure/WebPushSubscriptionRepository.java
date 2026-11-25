package br.com.controlegastos.notifications.infrastructure;

import br.com.controlegastos.notifications.domain.WebPushSubscription;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebPushSubscriptionRepository extends JpaRepository<WebPushSubscription, UUID> {

    Optional<WebPushSubscription> findByEndpoint(String endpoint);

    List<WebPushSubscription> findByUserId(UUID userId);

    @Modifying
    @Query("delete from WebPushSubscription s where s.endpoint = :endpoint and s.userId = :userId")
    int deleteOwned(@Param("endpoint") String endpoint, @Param("userId") UUID userId);
}
