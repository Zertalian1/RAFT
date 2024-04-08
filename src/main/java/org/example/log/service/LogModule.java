package org.example.log.service;

import org.example.log.entity.LogEntry;

import java.util.Map;

public interface LogModule {
    void write(LogEntry logEntry);

    LogEntry read(Long index);

    void applyToStateMachine(Long index);

    String[] get(String name);

    Map<String, String[]> getAll();

    void removeOnStartIndex(Long startIndex);

    LogEntry getLast();

    Long getLastIndex();

    LogEntry getPreLog(LogEntry logEntry);
}
