package org.example.log.serviceImpl;

import java.util.Map;

public class StateMachineService {
    InMemoryKVStateMachine machine = new InMemoryKVStateMachine();

    public StateMachineService() {
    }

    public void applyStateMachine(String command, String[] params) {
        switch (command) {
            case "set" -> machine.set(params);
        }
    }

    public Map<String, String[]> getAll() {
        return machine.getAll();
    }


    public String[] get(String name) {
        return machine.get(name);
    }
}
