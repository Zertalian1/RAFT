package org.example.rpc;

import com.alipay.remoting.BizContext;
import com.alipay.remoting.rpc.RpcServer;
import org.example.node.consensus.Consensus;
import org.example.node.entity.AentryParam;
import org.example.node.entity.RvoteParam;
import org.example.rpc.entity.RaftUserProcessor;
import org.example.rpc.entity.Request;
import org.example.rpc.entity.Response;
import org.example.rpc.entity.RpcCommand;

public class DefaultBaseRpcServer implements BaseRpcServer {
    /*Зупущен ли сервер*/
    private volatile boolean flag;

    private Consensus consensus;

    private RpcServer baseRpcServer;

    public DefaultBaseRpcServer(Consensus consensus, int serverPort) {

        if (flag) {
            return;
        }
        synchronized (this) {
            if (flag) {
                return;
            }
            /*ааа ыыы, можно ip дописать, но не хлчется, да будет локалхост*/
            baseRpcServer = new RpcServer(serverPort, false, false);
            baseRpcServer.registerUserProcessor(new RaftUserProcessor<Request<Object>>() {
                @Override
                public Object handleRequest(BizContext bizCtx, Request<Object> request) {
                    return handlerRequest(request);
                }
            });

            this.consensus = consensus;
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
            return new Response<>(consensus.requestVote((RvoteParam) request.getBody()));
        } else if (request.getCmd() == RpcCommand.A_ENTRIES.ordinal()) {
            return new Response<>(consensus.appendEntries((AentryParam) request.getBody()));
        }
        return null;
    }
}
