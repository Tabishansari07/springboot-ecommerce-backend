package com.tabish.SpringBootLab.model;

public enum OutboxEventStatus {
    PENDING,
    PROCESSING,
    PUBLISHED,
    FAILED
}
