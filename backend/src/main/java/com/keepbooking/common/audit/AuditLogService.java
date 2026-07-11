package com.keepbooking.common.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.keepbooking.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Transactional
    public void record(Long actorId, String action, String entityType, Long entityId, String details) {
        AuditLog entry = AuditLog.builder()
                .actor(actorId != null ? userRepository.getReferenceById(actorId) : null)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .details(details)
                .build();
        auditLogRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getForEntity(String entityType, Long entityId, Pageable pageable) {
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId, pageable);
    }
}
