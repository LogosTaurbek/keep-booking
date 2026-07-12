package com.keepbooking.notification.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.keepbooking.notification.model.NotificationOutbox;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {

    @Query("""
            SELECT o FROM NotificationOutbox o
            WHERE o.status = 'PENDING' AND o.nextAttemptAt <= :now
            ORDER BY o.createdAt ASC
            """)
    List<NotificationOutbox> findBatchToProcess(@Param("now") Instant now, Pageable pageable);
}
