package com.tabish.SpringBootLab.model;

public enum DeadLetterStatus {
    RECEIVED,
    QUARANTINED,
    REPLAYING,
    REPLAYED
}
