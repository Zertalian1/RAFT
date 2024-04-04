package org.example.log.service;

import org.example.log.entity.LogEntry;

public interface LogModule {
    void write(LogEntry logEntry);

    LogEntry read(Long index);

    void applyToStateMachine(Long index);

    String[] get(String name);

    boolean lockOperation(String name);
    void unlockOperation(String name);

    void removeOnStartIndex(Long startIndex);

    LogEntry getLast();

    Long getLastIndex();

    LogEntry getPreLog(LogEntry logEntry);
}
