package org.example.log.service;

import org.example.log.entity.LogEntry;

public interface StateMachine {
    void apply(LogEntry logEntry);

    LogEntry get(String key);

    String getString(String key);

    void setString(String key, String value);

    void delString(String... key);
}
