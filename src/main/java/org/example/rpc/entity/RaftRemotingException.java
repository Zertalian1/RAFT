package org.example.rpc.entity;

public class RaftRemotingException extends RuntimeException {

    public RaftRemotingException() {
        super();
    }

    public RaftRemotingException(String message) {
        super(message);
    }
}
