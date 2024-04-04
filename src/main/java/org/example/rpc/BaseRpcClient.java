package org.example.rpc;

import org.example.rpc.entity.RaftRemotingException;
import org.example.rpc.entity.Request;
import org.example.rpc.entity.Response;

public interface BaseRpcClient {
    Response send(Request request) throws RaftRemotingException;

    Response send(Request request, int timeout) throws RaftRemotingException;
}
