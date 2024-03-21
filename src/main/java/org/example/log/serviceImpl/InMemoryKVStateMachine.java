package org.example.log.serviceImpl;

import org.example.log.service.StateMachine;

import java.util.HashMap;
import java.util.Map;

public class InMemoryKVStateMachine implements StateMachine {
    private final Map<String, String> stateMachine = new HashMap<>();

    @Override
    public void apply(String[] params) {
        if (params == null) {
            throw new IllegalArgumentException("command params can not be null");
        }
        String key = params[0];
        String value = params[1];
        stateMachine.put(key, value);
    }

    @Override
    public String[] get(String name) {
        String value = stateMachine.get(name);
        if (value == null) {
            return null;
        }
        return new String[]{value};
    }
}
