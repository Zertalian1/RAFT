package org.example.task;

import org.example.node.DefaultNode;
import org.example.node.NodeStatus;
import org.example.node.entity.AentryParam;
import org.example.node.entity.AentryResult;
import org.example.rpc.entity.RpcCommand;
import org.example.rpc.entity.RaftRemotingException;
import org.example.rpc.entity.Request;
import org.example.rpc.entity.Response;
import org.example.server.Peer;
import org.example.thread.RaftThreadPool;

/*Лидер периодически отправляет heartbeat (исключая RPC приложения)
всем подписчикам для поддержания своего статуса. но с ним привычнее)
можно заменить на отправку моментальных снимков*/
public class BaseHeartBeatTask implements Runnable {
    private final DefaultNode node;

    public BaseHeartBeatTask(DefaultNode node) {
        this.node = node;
    }

    @Override
    public void run() {

        if (node.getNodeStatus() != NodeStatus.LEADER) {
            return;
        }

        long current = System.currentTimeMillis();
        if (current - node.getPreHeartBeatTime() < node.getHeartBeatTick()) {
            return;
        }

        node.setPreHeartBeatTime(System.currentTimeMillis());

        for (Peer peer : node.getPeerSet().getPeers()) {
            AentryParam param = new AentryParam();
            param.setEntries(null);
            param.setLeaderId("localhost:" + node.getPort());
            param.setTerm(node.getCurrentTerm());
            
            Request<AentryParam> request = new Request<>(
                    RpcCommand.A_ENTRIES.ordinal(),
                    param,
                    peer.getAddr());
            System.out.println("Лидер говорит, что он жив:  "+ request+" терм   "+ node.getCurrentTerm());
            RaftThreadPool.execute(() -> {
                try {
                    Response response = node.getRpcClient().send(request);
                    AentryResult aentryResult = (AentryResult) response.getResult();
                    long term = aentryResult.getTerm();

                    if (term > node.getCurrentTerm()) {
                        node.setCurrentTerm(term);
                        node.setVotedFor("");
                        node.setNodeStatusIndex(NodeStatus.FOLLOWER);
                    }
                } catch (RaftRemotingException ignored) {
                }
            }, false);
        }
    }
}
