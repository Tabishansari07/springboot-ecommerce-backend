package com.tabish.SpringBootLab.service;

import com.tabish.SpringBootLab.model.DeadLetterAuditAction;
import com.tabish.SpringBootLab.model.DeadLetterAuditLog;
import com.tabish.SpringBootLab.model.User;
import com.tabish.SpringBootLab.repository.DeadLetterAuditLogRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DeadLetterAuditService {

    private static final int MAX_DETAILS_LENGTH = 2_000;

    private final DeadLetterAuditLogRepository auditLogRepository;

    public DeadLetterAuditService(
            DeadLetterAuditLogRepository auditLogRepository
    ) {
        this.auditLogRepository = auditLogRepository;
    }

    public void record(
            UUID deadLetterEventId,
            DeadLetterAuditAction action,
            User actor,
            String details
    ) {
        auditLogRepository.save(
                new DeadLetterAuditLog(
                        deadLetterEventId,
                        action,
                        actor == null ? null : actor.getId(),
                        actor == null ? null : actor.getEmail(),
                        normalize(details)
                )
        );
    }

    private String normalize(String details) {
        if (details == null || details.isBlank()) {
            return null;
        }

        String trimmed = details.trim();
        return trimmed.substring(
                0,
                Math.min(trimmed.length(), MAX_DETAILS_LENGTH)
        );
    }
}
