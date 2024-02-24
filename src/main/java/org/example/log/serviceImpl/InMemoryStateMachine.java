package org.example.log.serviceImpl;

import org.example.log.entity.Command;
import org.example.log.entity.LogEntry;
import org.example.log.service.StateMachine;

import java.util.HashMap;
import java.util.Map;

public class InMemoryStateMachine implements StateMachine {
    private final Map<String, LogEntry> stateMachine = new HashMap<>();

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

    @Override
    public String getString(String key) {
        return stateMachine.get(key).toString();
    }

    @Override
    public void setString(String key, String value) {

    }

    @Override
    public void delString(String... key) {

    }
}
