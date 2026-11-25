package br.com.controlegastos.notifications.infrastructure;

import br.com.controlegastos.notifications.domain.NotificationPreference;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {

    /** Quem tem o lembrete ativo, horário vencido, ainda não foi lembrado hoje e tem onde receber. */
    @Query(value = """
            SELECT p.user_id FROM notification_preference p
            WHERE p.daily_reminder
              AND p.reminder_time <= :time
              AND (p.last_reminder_date IS NULL OR p.last_reminder_date < :today)
              AND EXISTS (SELECT 1 FROM web_push_subscription s WHERE s.user_id = p.user_id)
            """, nativeQuery = true)
    List<UUID> findDueForReminder(@Param("time") LocalTime time, @Param("today") LocalDate today);

    /** Marca o lembrete de hoje; devolve 0 se outra rodada já marcou (guarda de idempotência). */
    @Modifying
    @Transactional
    @Query(value = """
            UPDATE notification_preference SET last_reminder_date = :today
            WHERE user_id = :userId AND (last_reminder_date IS NULL OR last_reminder_date < :today)
            """, nativeQuery = true)
    int claimReminder(@Param("userId") UUID userId, @Param("today") LocalDate today);
}
