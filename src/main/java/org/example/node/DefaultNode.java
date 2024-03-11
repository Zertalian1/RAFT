package org.example.node;

import lombok.Getter;
import lombok.Setter;
import org.example.log.entity.Command;
import org.example.log.entity.LogEntry;
import org.example.log.service.LogModule;
import org.example.log.service.StateMachine;
import org.example.log.serviceImpl.InMemoryLogModule;
import org.example.log.serviceImpl.InMemoryStateMachine;
import org.example.node.consensus.Consensus;
import org.example.node.consensus.DefaultConsensus;
import org.example.node.entity.*;
import org.example.rpc.BaseRpcClient;
import org.example.rpc.BaseRpcServer;
import org.example.rpc.DefaultBaseRpcClient;
import org.example.rpc.DefaultBaseRpcServer;
import org.example.rpc.entity.Request;
import org.example.rpc.entity.Response;
import org.example.rpc.entity.RpcCommand;
import org.example.server.Peer;
import org.example.server.PeerSet;
import org.example.task.BaseHeartBeatTask;
import org.example.task.ElectionTask;
import org.example.task.ReplicationFailQueueConsumer;
import org.example.thread.RaftThreadPool;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

@Getter
@Setter
public class DefaultNode implements Node {
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
    /* Штука ответственная за то, чтобы спамить чообщениями о новой записи неответиыших подписчиков */
    private ReplicationFailQueueConsumer replicationFailQueueConsumer = new ReplicationFailQueueConsumer(this);
    volatile long lastApplied = 0;
    /* Значение индекса самой большой известной записи журнала, которая была отправлена */
    volatile long commitIndex;
    /*а тут вобщем о вся логика*/
    Consensus consensus;
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
            RaftThreadPool.execute(replicationFailQueueConsumer);

            LogEntry logEntry = logModule.getLast();
            if (logEntry != null) {
                currentTerm = logEntry.getTerm();
            }

            started = true;
        }
    }

    @Override
    public RvoteResult handlerRequestVote(RvoteParam param) {
        System.out.println("Принял голос:   " + param + " терм   " + param.getTerm());
        return consensus.requestVote(param);
    }

    @Override
    public void destroy() {
        rpcServer.stop();
    }

    public Object handlerAppendEntries(AentryParam param) {
        System.out.println("Принял инфу о жизни лидера:   " + param + " терм   " + param.getTerm());
        return consensus.appendEntries(param);
    }

    public synchronized boolean addEntry(String key, String value) {
        if (getNodeStatus() != NodeStatus.LEADER) {
            System.out.println("I'm not a leader");
            return false;
        }
        LogEntry logEntry = new LogEntry();
        logEntry.setCommand(new Command(key, value));
        logEntry.setTerm(getCurrentTerm());

        logModule.write(logEntry);
        final AtomicInteger success = new AtomicInteger(0);

        List<Future<Boolean>> futureList = new CopyOnWriteArrayList<>();
        //  скидываем всем остальным
        for (Peer peer : peerSet.getPeers()) {
            futureList.add(replication(peer, logEntry));
        }
        CountDownLatch latch = new CountDownLatch(futureList.size());
        List<Boolean> resultList = new CopyOnWriteArrayList<>();

        getRPCAppendResult(futureList, latch, resultList);

        try {
            latch.await(4000, MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        for (Boolean aBoolean : resultList) {
            if (aBoolean) {
                success.incrementAndGet();
            }
        }
        /*Если существует N, такое, что N> commitIndex, most matchIndex[I]≥N и log[N].
        Term == currentTerm: установить commitIndex = N*/
        List<Long> matchIndexList = new ArrayList<>(matchIndexs.values());
        int median = 0;
        if (matchIndexList.size() >= 2) {
            Collections.sort(matchIndexList);
            median = matchIndexList.size() / 2;
        }
        Long N = matchIndexList.get(median);
        if (N > commitIndex) {
            LogEntry entry = logModule.read(N);
            if (entry != null && entry.getTerm() == currentTerm) {
                commitIndex = N;
            }
        }

        if (success.get() >= (peerSet.getPeers().size() / 2)) {
            commitIndex = logEntry.getIndex();
            getStateMachine().apply(logEntry);
            lastApplied = commitIndex;
            return true;
        } else {
            logModule.removeOnStartIndex(logEntry.getIndex());
            // TODO ЫЫЫ АА, а что делать то - повторять?
            System.out.println("The limit of half copies has not been reached");
            return false;
        }
    }

    /*гемор, эта штука должна
    1 - при получении ошибки начать перебирать журнал в обратном порядкеб пока не найдут общее
    2 - отправлять записи и при провале закидывать их ReplicationFailQueueConsumer для спама
    яэбал*/
    private Future<Boolean> replication(Peer peer, LogEntry entry) {
        return RaftThreadPool.submit(new Callable() {
            @Override
            public Boolean call() throws Exception {

                long start = System.currentTimeMillis(), end = start;

            /*Raft RPC требуют от получателя сохранения информации в стабильном хранилище,
            поэтому время трансляции может составлять от 0,5 до 20 мс в зависимости от технологии хранения.*/
                while (end - start < 20 * 1000L) {

                    AentryParam aentryParam = new AentryParam();
                    aentryParam.setTerm(currentTerm);
                    aentryParam.setLeaderId("localhost:" + getPort());
                    aentryParam.setLeaderCommit(commitIndex);

                    Long nextIndex = nextIndexs.get(peer);
                    LinkedList<LogEntry> logEntries = new LinkedList<>();
                    /*штука для выполнгения 1-го условия*/
                    if (entry.getIndex() >= nextIndex) {
                        for (long i = nextIndex; i <= entry.getIndex(); i++) {
                            LogEntry l = logModule.read(i);
                            if (l != null) {
                                logEntries.add(l);
                            }
                        }
                    } else {
                        logEntries.add(entry);
                    }

                    LogEntry preLog = getPreLog(logEntries.getFirst());
                    aentryParam.setPreLogTerm(preLog.getTerm());
                    aentryParam.setPrevLogIndex(preLog.getIndex());

                    aentryParam.setEntries(logEntries.toArray(new LogEntry[0]));
                    Request<AentryParam> request = new Request<>(
                            RpcCommand.A_ENTRIES.ordinal(),
                            aentryParam,
                            peer.getAddr());

                    try {
                        Response response = getRpcClient().send(request);
                        if (response == null) {
                            return false;
                        }
                        AentryResult result = (AentryResult) response.getResult();
                        if (result != null && result.isSuccess()) {
                            System.out.printf("append follower entry success , follower=[{%s}] \n", peer);
                            nextIndexs.put(peer, entry.getIndex() + 1);
                            matchIndexs.put(peer, entry.getIndex());
                            return true;
                        } else if (result != null) {
                            if (result.getTerm() > currentTerm) {
                                System.out.printf("follower [{%s}] term [{%d}], my term = [{%d}], become to follower \n",
                                        peer, result.getTerm(), currentTerm);
                                currentTerm = result.getTerm();
                                setNodeStatusIndex(NodeStatus.FOLLOWER);
                                return false;
                            } else {
                                if (nextIndex == 0) {
                                    nextIndex = 1L;
                                }
                                nextIndexs.put(peer, nextIndex - 1);
                                System.out.printf(
                                        "follower {%s} nextIndex not match, will reduce nextIndex and retry RPC append, nextIndex : [{%d}]",
                                        peer.getAddr(),
                                        nextIndex
                                );
                            }
                        }
                    } catch (Exception e) {

                        ReplicationFailModel model = ReplicationFailModel.builder()
                                .callable(this)
                                .logEntry(entry)
                                .peer(peer)
                                .offerTime(System.currentTimeMillis())
                                .build();
                        replicationFailQueueConsumer.addToQueue(model);
                        return false;
                    }
                    end = System.currentTimeMillis();
                }
                return false;
            }
        });
    }

    private LogEntry getPreLog(LogEntry logEntry) {
        LogEntry entry = logModule.read(logEntry.getIndex() - 1);

        if (entry == null) {
            entry = new LogEntry(0L, 0, null);
        }
        return entry;
    }

    private void getRPCAppendResult(List<Future<Boolean>> futureList, CountDownLatch latch, List<Boolean> resultList) {
        for (Future<Boolean> future : futureList) {
            RaftThreadPool.execute(() -> {
                try {
                    resultList.add(future.get(3000, MILLISECONDS));
                } catch (CancellationException | TimeoutException | ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                    resultList.add(false);
                } finally {
                    latch.countDown();
                }
            });
        }
    }

    public synchronized String getEntry(String key) {
        LogEntry logEntry = stateMachine.get(key);
        if (logEntry != null) {
            return logEntry.getCommand().getKey();
        }
        return null;
    }
}
