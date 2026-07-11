package com.keepbooking.common.audit;

import java.time.Instant;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuditLogDto {
    private Long id;
    private Long actorId;
    private String action;
    private String entityType;
    private Long entityId;
    private String details;
    private Instant createdAt;

    static AuditLogDto from(AuditLog log) {
        return AuditLogDto.builder()
                .id(log.getId())
                .actorId(log.getActor() != null ? log.getActor().getId() : null)
                .action(log.getAction())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .details(log.getDetails())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
