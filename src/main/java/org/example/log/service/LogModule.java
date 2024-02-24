package org.example.log.service;

import org.example.log.entity.LogEntry;

public interface LogModule {
    void write(LogEntry logEntry);

    LogEntry read(Long index);

    void removeOnStartIndex(Long startIndex);

    LogEntry getLast();

    Long getLastIndex();
}
