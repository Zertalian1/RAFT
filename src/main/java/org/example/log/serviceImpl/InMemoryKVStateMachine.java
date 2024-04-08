package org.example.log.serviceImpl;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

public class InMemoryKVStateMachine {
    private final Map<String, String[]> stateMachine = new HashMap<>();

    public void set(String[] params) {
        if (params == null) {
            throw new IllegalArgumentException("command params can not be null");
        }
        String key = params[0];
        String[] value = {params[1]};
        stateMachine.put(key, value);
    }
    public Map<String, String[]> getAll() {
        return stateMachine;
    }

    public String[] get(String name) {
        String[] value = stateMachine.get(name);
        if (value != null && value.length == 0) {
            return null;
        }
        return value;
    }
}
