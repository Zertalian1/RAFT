package org.example.rpc;

import org.example.rpc.entity.Request;
import org.example.rpc.entity.Response;

import java.io.Serializable;

public interface BaseRpcServer {
    void start();

    void stop();

    Response<Object> handlerRequest(Request<Object> request);
}
