package org.example.log.serviceImpl;

public class StateMachineService {
    InMemoryKVStateMachine machine = new InMemoryKVStateMachine();

    public StateMachineService() {
    }

    public void applyStateMachine(String command, String[] params) {
        switch (command) {
            case "set" -> machine.set(params);
        }
    }

    boolean lockOperation(String name) {
        return machine.lock(name);
    }
    void unlockOperation(String name) {
        machine.unlock(name);
    }

    public String[] get(String name) {
        return machine.get(name);
    }
}
