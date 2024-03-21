package org.example.log.service;

public interface StateMachine {
    void apply(String[] params);

    String[] get(String name);
}
