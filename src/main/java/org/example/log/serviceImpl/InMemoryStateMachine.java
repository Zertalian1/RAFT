package org.example.log.serviceImpl;

import org.example.log.entity.Command;
import org.example.log.entity.LogEntry;
import org.example.log.service.StateMachine;

import java.util.HashMap;
import java.util.Map;

public class InMemoryStateMachine implements StateMachine {
    private final Map<String, LogEntry> stateMachine = new HashMap<>();
    private final Map<String, Integer> successIndexes = new HashMap<>();

    public Integer getSuccessIndex(String key) {
        return successIndexes.get(key);
    }

    @Override
    public void  deleteSuccessIndex(String key) {
        successIndexes.remove(key);
    }

    public void setSuccessIndex(String key, Integer successIndex) {
        successIndexes.put(key, successIndex);
    }

    @Override
    public void apply(LogEntry logEntry) {
        Command command = logEntry.getCommand();
        if (command == null) {
            throw new IllegalArgumentException("command can not be null, logEntry : " + logEntry);
        }
        String key = command.getKey();
        stateMachine.put(key, logEntry);
    }

    @Override
    public LogEntry get(String key) {
        return stateMachine.get(key);
    }
}
