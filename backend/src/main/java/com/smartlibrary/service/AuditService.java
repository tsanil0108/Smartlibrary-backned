package com.smartlibrary.service;

import com.smartlibrary.entity.AuditLog;
import com.smartlibrary.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;

    public void log(Long actorUserId, String action, String entityType, Long entityId, String details) {
        AuditLog log = new AuditLog();
        log.setActorUserId(actorUserId);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setDetails(details != null && details.length() > 500 ? details.substring(0, 500) : details);
        repository.save(log);
    }
}
