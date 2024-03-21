package org.example.node;

import org.example.log.entity.LogEntry;
import org.example.log.service.LogModule;
import org.example.node.entity.AentryParam;
import org.example.node.entity.AentryResult;
import org.example.node.entity.ReplicationFailModel;
import org.example.rpc.BaseRpcClient;
import org.example.rpc.entity.Request;
import org.example.rpc.entity.Response;
import org.example.rpc.entity.RpcCommand;
import org.example.server.Peer;
import org.example.server.PeerSet;
import org.example.task.ReplicationFailQueueConsumer;
import org.example.thread.RaftThreadPool;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class ReplicationService {
    private final DefaultNode node;
    private final LogModule logModule;
    private final PeerSet peerSet;
    private final BaseRpcClient rpcClient;
    private final ReplicationFailQueueConsumer replicationFailQueueConsumer;

    public ReplicationService(
            DefaultNode node,
            LogModule logModule,
            PeerSet peerSet,
            BaseRpcClient rpcClient,
            ReplicationFailQueueConsumer replicationFailQueueConsumer
    ) {
        this.node = node;
        this.logModule = logModule;
        this.peerSet = peerSet;
        this.rpcClient = rpcClient;
        this.replicationFailQueueConsumer = replicationFailQueueConsumer;
    }

    public synchronized void addEntry(LogEntry logEntry) {
        if (node.getNodeStatus() != NodeStatus.LEADER) {
            System.out.println("I'm not a leader, leader is: " + peerSet.getLeader());
            return;
        }
        logEntry.setTerm(node.getCurrentTerm());
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
        List<Long> matchIndexList = new ArrayList<>(node.getMatchIndexs().values());
        int median = 0;
        if (!matchIndexList.isEmpty()) {
            Collections.sort(matchIndexList);
            median = matchIndexList.size() / 2;
        }
        Long N = matchIndexList.get(median);
        if (N > node.getCommitIndex()) {
            LogEntry entry = logModule.read(N);
            if (entry != null && entry.getTerm() == node.getCurrentTerm()) {
                node.setCommitIndex(N);
            }
        }

        if (success.get() >= peerSet.getPeers().size() / 2.0) {
            node.setCommitIndex(logEntry.getIndex());
            logModule.applyToStateMachine(logEntry.getIndex());
            node.setLastApplied(node.getCommitIndex());
        } else {
            replicationFailQueueConsumer.setSuccessIndex(logEntry.getIndex().toString()+logEntry.getTerm(), success.get());
            System.out.println("The limit of half copies has not been reached");
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
                    aentryParam.setTerm(node.getCurrentTerm());
                    aentryParam.setLeaderId("localhost:" + peerSet.getPort());
                    aentryParam.setLeaderCommit(node.getCommitIndex());

                    Long nextIndex = node.getNextIndexs().get(peer);
                    LinkedList<LogEntry> logEntries = new LinkedList<>();
                    /*штука для выполнгения 1-го условия*/
                    if (entry.getIndex() > nextIndex) {
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
                        Response response = rpcClient.send(request);
                        if (response == null) {
                            return false;
                        }
                        AentryResult result = (AentryResult) response.getResult();
                        if (result != null && result.isSuccess()) {
                            System.out.printf("append follower entry success , follower=[{%s}] \n", peer);
                            node.getNextIndexs().put(peer, entry.getIndex() + 1);
                            node.getMatchIndexs().put(peer, entry.getIndex());
                            return true;
                        } else if (result != null) {
                            if (result.getTerm() > node.getCurrentTerm()) {
                                System.out.printf("follower [{%s}] term [{%d}], my term = [{%d}], become to follower \n",
                                        peer, result.getTerm(), node.getCurrentTerm());
                                node.setCurrentTerm(result.getTerm());
                                node.setNodeStatusIndex(NodeStatus.FOLLOWER);
                                return false;
                            } else {
                                if (nextIndex == 0) {
                                    nextIndex = 1L;
                                }
                                node.getNextIndexs().put(peer, nextIndex - 1);
                                System.out.printf(
                                        "follower {%s} nextIndex not match, will reduce nextIndex and retry RPC append, nextIndex : [{%d}]",
                                        peer.getAddr(),
                                        nextIndex
                                );
                            }
                        }
                    } catch (Exception e) {
                        System.out.println(Arrays.toString(e.getStackTrace()));
                        ReplicationFailModel model = ReplicationFailModel.builder()
                                .callable(this)
                                .successKey(entry.getIndex().toString()+entry.getTerm())
                                .logEntry(entry)
                                .peer(peer)
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
                    resultList.add(false);
                } finally {
                    latch.countDown();
                }
            });
        }
    }
}
