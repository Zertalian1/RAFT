package org.example.log.serviceImpl;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

public class InMemoryKVStateMachine {
    private final Map<String, String> stateMachine = new HashMap<>();
    private final Map<String, Semaphore> mutexList = new HashMap<>();

    private final Semaphore mutex1 = new Semaphore(1);

    public boolean lock(String key) {
        if (!mutex1.tryAcquire()) {
            return false;
        }
        Semaphore mutex = mutexList.computeIfAbsent(key, k -> new Semaphore(1));
        boolean result = mutex.tryAcquire();
        mutex1.release();
        return result;
    }
    public void unlock(String key) {
        Semaphore mutex = mutexList.get(key);
        mutex.release();
    }

    public void set(String[] params) {
        if (params == null) {
            throw new IllegalArgumentException("command params can not be null");
        }
        String key = params[0];
        String value = params[1];
        stateMachine.put(key, value);
    }

    public String[] get(String name) {
        String value = stateMachine.get(name);
        if (value == null) {
            return null;
        }
        return new String[]{value};
    }
}
