package org.example.rpc.entity;

public class RaftRemotingException extends Exception {

    public RaftRemotingException() {
        super();
    }

    public RaftRemotingException(String message) {
        super(message);
    }
}
