package com.taskpulse.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TaskPriority {
    CRITICAL(1),
    HIGH(2),
    NORMAL(3),
    LOW(4);

    private final int rank;
}
