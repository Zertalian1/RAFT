package org.example.log.service;

import org.example.log.entity.LogEntry;

public interface StateMachine {
    void apply(LogEntry logEntry);

    LogEntry get(String key);

    Integer getSuccessIndex(String key);

    void setSuccessIndex(String key, Integer value);

    Integer getCount();

    void setCount(Integer count);
}
