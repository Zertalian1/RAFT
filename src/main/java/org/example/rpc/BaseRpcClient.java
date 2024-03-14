package org.example.rpc;

import org.example.rpc.entity.Request;
import org.example.rpc.entity.Response;

public interface BaseRpcClient {
    Response send(Request request);

    Response send(Request request, int timeout);
}
