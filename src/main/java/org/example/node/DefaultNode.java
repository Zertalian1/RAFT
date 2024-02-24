package org.example.node;

import lombok.Getter;
import lombok.Setter;
import org.example.log.serviceImpl.InMemoryLogModule;
import org.example.log.service.LogModule;
import org.example.log.serviceImpl.InMemoryStateMachine;
import org.example.log.service.StateMachine;
import org.example.node.consensus.Consensus;
import org.example.node.consensus.DefaultConsensus;
import org.example.node.entity.AentryParam;
import org.example.node.entity.RvoteParam;
import org.example.node.entity.RvoteResult;
import org.example.rpc.BaseRpcClient;
import org.example.rpc.DefaultBaseRpcClient;
import org.example.rpc.DefaultBaseRpcServer;
import org.example.server.Peer;
import org.example.server.PeerSet;
import org.example.rpc.BaseRpcServer;
import org.example.task.ElectionTask;
import org.example.task.BaseHeartBeatTask;
import org.example.task.ReplicationFailQueueConsumer;
import org.example.thread.RaftThreadPool;

@Getter
@Setter
public class DefaultNode implements Node{
    /*Запущенна ли нода*/
    private volatile boolean started;
    /*Таймаут для выборов*/
    private volatile long electionTime = 15 * 1000;
    /*Время с предыдущих выборов*/
    private volatile long preElectionTime = 0;
    /*Время с предыдущего серцебиения*/
    private volatile long preHeartBeatTime = 0;
    /*LEADER переодически шлёт сообщения FOLLOWER о том, что он жив*/
    private final long heartBeatTick = 5 * 1000;
    /*Сервер*/
    private BaseRpcServer rpcServer;
    /*Клиент*/
    private BaseRpcClient rpcClient;
    /*Ноды*/
    private PeerSet peerSet;
    /*порт ноды*/
    private int port;
    /*Статус ноды*/
    volatile int nodeStatusIndex;
    /*терм, увеличивается на каждой новой эпохе)*/
    volatile long currentTerm = 0;
    /*журнал логов*/
    LogModule logModule;
    /*машина состояний - тип конечный автомат*/
    public StateMachine stateMachine;
    /**/
    private ReplicationFailQueueConsumer replicationFailQueueConsumer = new ReplicationFailQueueConsumer();
    /*а тут вобщем о вся логика*/
    Consensus consensus;
    /*за кого голосует узел*/
    volatile String votedFor;
    /*таска сердцебиения сервера*/
    private BaseHeartBeatTask baseHeartBeatTask;
    /*таска выбора лидера*/
    private ElectionTask electionTask;

    public void setNodeStatusIndex(NodeStatus nodeStatusIndex) {
        this.nodeStatusIndex = nodeStatusIndex.ordinal();
    }

    public DefaultNode(NodeConfig config) {
        setConfig(config);
    }

    public NodeStatus getNodeStatus() {
        return NodeStatus.values()[nodeStatusIndex];
    }

    public synchronized void newTerm() {
        ++currentTerm;
    }

    @Override
    public void setConfig(NodeConfig config) {
        peerSet = new PeerSet();
        for (String s : config.getPeerAddress()) {
            Peer peer = new Peer(s);
            peerSet.addPeer(peer);
        }
        port = config.selfPort;
        rpcServer = new DefaultBaseRpcServer(this);
        rpcClient = new DefaultBaseRpcClient();
        stateMachine = new InMemoryStateMachine();
        logModule = new InMemoryLogModule();
        this.baseHeartBeatTask = new BaseHeartBeatTask(this);
        this.electionTask = new ElectionTask(this);
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
            consensus = new DefaultConsensus(this);

            RaftThreadPool.scheduleWithFixedDelay(baseHeartBeatTask, 500);
            RaftThreadPool.scheduleAtFixedRate(electionTask, 6000, 500);

            started = true;

        }
    }

    @Override
    public RvoteResult handlerRequestVote(RvoteParam param) {
        System.out.println("Принял голос:   "+param+" терм   "+ param.getTerm());
        return consensus.requestVote(param);
    }

    @Override
    public void destroy() {
        rpcServer.stop();
    }

    public Object handlerAppendEntries(AentryParam param) {
        System.out.println("Принял инфу о жизни лидера:   "+param+" терм   "+ param.getTerm());
        return consensus.appendEntries(param);
    }
}
