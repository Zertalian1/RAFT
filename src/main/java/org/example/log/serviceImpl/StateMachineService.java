package org.example.log.serviceImpl;

import org.example.log.service.StateMachine;

import java.util.HashMap;
import java.util.Map;

public class StateMachineService {
    private final Map<String, StateMachine> stateMachineList = new HashMap<>();

    public StateMachineService() {
        stateMachineList.put("KVStorage", new InMemoryKVStateMachine());
    }

    public void applyStateMachine(String command, String[] params) {
        StateMachine stateMachine = stateMachineList.get(command);
        stateMachine.apply(params);
    }

    public String[] get(String command, String name) {
        StateMachine stateMachine = stateMachineList.get(command);
        return stateMachine.get(name);
    }
}
