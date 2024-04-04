package org.example.node;

import lombok.Getter;
import lombok.Setter;
import org.example.log.entity.LogEntry;
import org.example.log.service.LogModule;
import org.example.log.serviceImpl.InMemoryLogModule;
import org.example.node.consensus.Consensus;
import org.example.node.consensus.DefaultConsensus;
import org.example.rpc.BaseRpcClient;
import org.example.rpc.BaseRpcServer;
import org.example.rpc.DefaultBaseRpcClient;
import org.example.rpc.DefaultBaseRpcServer;
import org.example.server.Peer;
import org.example.server.PeerSet;
import org.example.task.BaseHeartBeatTask;
import org.example.task.ElectionTask;
import org.example.thread.RaftThreadPool;

import java.util.Map;
import java.util.concurrent.Semaphore;

@Getter
@Setter
public class DefaultNode implements Node {
    /*Запущенна ли нода*/
    private volatile boolean started;
    /*Сервер*/
    private BaseRpcServer rpcServer;
    /*Статус ноды*/
    volatile int nodeStatusIndex;
    /*терм, увеличивается на каждой новой эпохе*/
    volatile long currentTerm = 0;
    /*журнал логов*/
    LogModule logModule;
    volatile long lastApplied = 0;
    /* Значение индекса самой большой известной записи журнала, которая была отправлена */
    volatile long commitIndex;
    /*за кого голосует узел*/
    volatile String votedFor;
    /*таска сердцебиения сервера*/
    private BaseHeartBeatTask baseHeartBeatTask;
    /*таска выбора лидера*/
    private ElectionTask electionTask;
    /*индекс следующей отправляемой записи для каждого узла*/
    Map<Peer, Long> nextIndexs;
    /*индекс последней сохранённой записи для каждого узла*/
    Map<Peer, Long> matchIndexs;
    Semaphore mutex = new Semaphore(1);

    public void lokMutex() {
        try {
            mutex.acquire();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void unlockMutex() {
        mutex.release();
    }

    public void setNodeStatusIndex(NodeStatus nodeStatusIndex) {
        this.nodeStatusIndex = nodeStatusIndex.ordinal();
    }

    public DefaultNode(NodeConfig config, SyncObjConsumer consumer) {
        setConfig(config, consumer);
    }

    public NodeStatus getNodeStatus() {
        return NodeStatus.values()[nodeStatusIndex];
    }

    public synchronized void newTerm() {
        ++currentTerm;
    }

    @Override
    public void setConfig(NodeConfig config, SyncObjConsumer consumer) {
        PeerSet peerSet = new PeerSet();
        for (String s : config.getPeerAddress()) {
            Peer peer = new Peer(s);
            peerSet.addPeer(peer);
        }
        peerSet.setPort(config.selfPort);
        logModule = new InMemoryLogModule();
        BaseRpcClient rpcClient = new DefaultBaseRpcClient();
        this.electionTask = new ElectionTask(this, rpcClient, peerSet, logModule);
        this.baseHeartBeatTask = new BaseHeartBeatTask(this, rpcClient, peerSet, logModule);
        Consensus consensus = new DefaultConsensus(this, electionTask, logModule, peerSet);
        rpcServer = new DefaultBaseRpcServer(consensus, config.selfPort);
        consumer.setLogModule(logModule);
        consumer.setReplicationService(new ReplicationService(
                this, logModule, peerSet, rpcClient
        ));
    }

    @Override
    public void init() {
        if (started) {
            return;
        }
        synchronized (this) {
            if (started) {
                return;
            }
            rpcServer.start();
            RaftThreadPool.scheduleWithFixedDelay(baseHeartBeatTask, 500);
            RaftThreadPool.scheduleAtFixedRate(electionTask, 6000, 500);
            LogEntry logEntry = logModule.getLast();
            if (logEntry != null) {
                currentTerm = logEntry.getTerm();
            }

            started = true;
        }
    }

    @Override
    public void destroy() {
        rpcServer.stop();
    }
}
