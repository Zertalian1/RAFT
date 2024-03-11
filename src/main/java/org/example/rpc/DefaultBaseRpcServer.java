package org.example.rpc;

import com.alipay.remoting.BizContext;
import com.alipay.remoting.rpc.RpcServer;
import org.example.node.DefaultNode;
import org.example.node.entity.AentryParam;
import org.example.node.entity.RvoteParam;
import org.example.rpc.entity.RpcCommand;
import org.example.rpc.entity.RaftUserProcessor;
import org.example.rpc.entity.Request;
import org.example.rpc.entity.Response;

public class DefaultBaseRpcServer implements BaseRpcServer {
    /*Зупущен ли сервер*/
    private volatile boolean flag;

    private DefaultNode node;

    private RpcServer baseRpcServer;

    public DefaultBaseRpcServer(DefaultNode node) {

        if (flag) {
            return;
        }
        synchronized (this) {
            if (flag) {
                return;
            }
            /*ааа ыыы, можно ip дописать, но не хлчется, да будет локалхост*/
            baseRpcServer = new RpcServer(node.getPort(), false, false);
            baseRpcServer.registerUserProcessor(new RaftUserProcessor<Request<Object>>() {
                @Override
                public Object handleRequest(BizContext bizCtx, Request<Object> request) {
                    return handlerRequest(request);
                }
            });

            this.node = node;
            flag = true;
        }

    }

    @Override
    public void start() {
        baseRpcServer.startup();
    }

    @Override
    public void stop() {
        baseRpcServer.shutdown();
    }

    @Override
    public Response<Object> handlerRequest(Request<Object> request) {
        if (request.getCmd() == RpcCommand.R_VOTE.ordinal()) {
            return new Response<>(node.handlerRequestVote((RvoteParam) request.getBody()));
        } else if (request.getCmd() == RpcCommand.A_ENTRIES.ordinal()) {
            return new Response<>(node.handlerAppendEntries((AentryParam) request.getBody()));
        }
        return null;
    }
}
