package org.example.task;

import org.example.log.entity.LogEntry;
import org.example.log.service.LogModule;
import org.example.node.DefaultNode;
import org.example.node.NodeStatus;
import org.example.node.entity.AentryParam;
import org.example.node.entity.AentryResult;
import org.example.node.entity.ReplicationFailModel;
import org.example.rpc.BaseRpcClient;
import org.example.rpc.entity.RpcCommand;
import org.example.rpc.entity.RaftRemotingException;
import org.example.rpc.entity.Request;
import org.example.rpc.entity.Response;
import org.example.server.Peer;
import org.example.server.PeerSet;
import org.example.thread.RaftThreadPool;

import java.util.LinkedList;

/*Лидер периодически отправляет heartbeat (исключая RPC приложения)
всем подписчикам для поддержания своего статуса. но с ним привычнее)
можно заменить на отправку моментальных снимков*/
public class BaseHeartBeatTask implements Runnable {
    private final DefaultNode node;
    /*Время с предыдущего серцебиения*/
    private volatile long preHeartBeatTime = 0;
    /*LEADER переодически шлёт сообщения FOLLOWER о том, что он жив*/
    private final BaseRpcClient rpcClient;
    private final long heartBeatTick = 5 * 1000;
    private final PeerSet peerSet;

    private final LogModule logModule;

    public BaseHeartBeatTask(DefaultNode node, BaseRpcClient rpcClient, PeerSet peerSet, LogModule logModule) {
        this.node = node;
        this.rpcClient = rpcClient;
        this.peerSet = peerSet;
        this.logModule = logModule;
    }

    @Override
    public void run() {

        if (node.getNodeStatus() != NodeStatus.LEADER) {
            return;
        }
        node.lokMutex();
        if (node.getNodeStatus() != NodeStatus.LEADER) {
            node.unlockMutex();
            return;
        }
        long current = System.currentTimeMillis();
        if (current - preHeartBeatTime < heartBeatTick) {
            node.unlockMutex();
            return;
        }
        preHeartBeatTime = System.currentTimeMillis();
        System.out.println("Лидер говорит, что он жив:  "+" терм   "+ node.getCurrentTerm());
        for (Peer peer : peerSet.getPeers()) {
            replication(peer);
            /*RaftThreadPool.execute(() -> {
                replication(peer);
            }, false);*/
        }
        node.unlockMutex();
    }

    private void replication(Peer peer) {
        long start = System.currentTimeMillis(), end = start;
        //while (end - start < 100L) {

            AentryParam aentryParam = new AentryParam();
            aentryParam.setTerm(node.getCurrentTerm());
            aentryParam.setLeaderId("localhost:" + peerSet.getPort());
            aentryParam.setLeaderCommit(node.getCommitIndex());

            Long nextIndex = node.getNextIndexs().get(peer);
            LinkedList<LogEntry> logEntries = new LinkedList<>();
            /*System.out.printf(
                    "follower {%s} nextIndex : [{%d}], lastApplied{%d} \n",
                    peer.getAddr(),
                    nextIndex,
                    node.getLastApplied()
            );*/
            if (node.getLastApplied() > nextIndex) {
                for (long i = nextIndex; i <= node.getLastApplied(); i++) {
                    LogEntry l = logModule.read(i);
                    if (l != null) {
                        logEntries.add(l);
                    }
                }
            }

            LogEntry preLog;
            if (!logEntries.isEmpty()){
                preLog = logModule.getPreLog(logEntries.getFirst());
                aentryParam.setPreLogTerm(preLog.getTerm());
                aentryParam.setPrevLogIndex(preLog.getIndex());
            } else {
                long last = node.getLastApplied();
                if (last != 0) {
                    preLog = logModule.getPreLog(logModule.read(last));
                    aentryParam.setPreLogTerm(preLog.getTerm());
                    aentryParam.setPrevLogIndex(preLog.getIndex());
                }
            }



            aentryParam.setEntries(logEntries.toArray(new LogEntry[0]));
            Request<AentryParam> request = new Request<>(
                    RpcCommand.A_ENTRIES.ordinal(),
                    aentryParam,
                    peer.getAddr());

            try {
                Response response = rpcClient.send(request);
                if (response == null) {
                    return;
                }
                AentryResult result = (AentryResult) response.getResult();
                if (result != null && result.isSuccess()) {
                    LogEntry logEntry = logModule.read(node.getLastApplied());
                    if (nextIndex <= node.getLastApplied() && logEntry != null) {
                        node.getNextIndexs().put(peer, node.getLastApplied() + 1);
                        node.getMatchIndexs().put(peer, logEntry.getIndex());
                    }
                    return;
                } else if (result != null) {
                    if (result.getTerm() > node.getCurrentTerm()) {
                        node.setCurrentTerm(result.getTerm());
                        node.setNodeStatusIndex(NodeStatus.FOLLOWER);
                        return;
                    } else {
                        if (nextIndex > result.getLastApl()) {
                            nextIndex = result.getLastApl()+1;
                        }
                        if (nextIndex == 0) {
                            nextIndex = 1L;
                        }
                        node.getNextIndexs().put(peer, nextIndex-1);
                        System.out.printf(
                                "follower {%s} nextIndex not match, will reduce nextIndex and retry RPC append, nextIndex : [{%d}]",
                                peer.getAddr(),
                                nextIndex-1
                        );
                    }
                }
            } catch (Exception e) {
                return;
            }
            end = System.currentTimeMillis();
        //}
    }

}
