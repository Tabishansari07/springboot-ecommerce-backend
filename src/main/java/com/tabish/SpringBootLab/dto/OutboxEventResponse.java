package com.tabish.SpringBootLab.dto;

import com.tabish.SpringBootLab.model.OutboxEventStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record OutboxEventResponse(
        UUID id,
        String aggregateType,
        String aggregateId,
        String eventType,
        String topic,
        String correlationId,
        String payload,
        OutboxEventStatus status,
        Integer retryCount,
        LocalDateTime createdAt,
        LocalDateTime publishedAt,
        String lastError
) {
}
