package org.example.node.consensus;

import io.grpc.netty.shaded.io.netty.util.internal.StringUtil;
import org.example.log.entity.LogEntry;
import org.example.log.service.LogModule;
import org.example.node.DefaultNode;
import org.example.node.NodeStatus;
import org.example.node.entity.AentryParam;
import org.example.node.entity.AentryResult;
import org.example.node.entity.RvoteParam;
import org.example.node.entity.RvoteResult;
import org.example.server.Peer;
import org.example.server.PeerSet;
import org.example.task.ElectionTask;

import java.util.concurrent.locks.ReentrantLock;

public class DefaultConsensus implements Consensus {
    public final DefaultNode node;
    public final ReentrantLock voteLock = new ReentrantLock();
    public final ReentrantLock appendLock = new ReentrantLock();
    public LogModule logModule;
    public ElectionTask electionTask;
    private final PeerSet peerSet;

    public DefaultConsensus(DefaultNode node, ElectionTask electionTask, LogModule logModule, PeerSet peerSet) {
        this.node = node;
        this.electionTask = electionTask;
        this.logModule = logModule;
        this.peerSet = peerSet;
    }

    @Override
    public RvoteResult requestVote(RvoteParam param) {
        try {
            RvoteResult.RvoteResultBuilder builder = RvoteResult.builder();
            if (!voteLock.tryLock()) {
                return builder.term(node.getCurrentTerm()).voteGranted(false).build();
            }
            /*Ответить ложно, если термин < текущий термин*/
            if (param.getTerm() < node.getCurrentTerm()) {
                return builder.term(node.getCurrentTerm()).voteGranted(false).build();
            }
            /*Каждый серверный узел будет голосовать только за одного кандидата на
            один и тот же срок в порядке поступления заявок*/
            if ((StringUtil.isNullOrEmpty(node.getVotedFor()) || node.getVotedFor().equals(param.getCandidateId()))) {
                if (logModule.getLast() != null) {
                    /*Если журнал отслеживания не содержит записи с термином, совпадающим с
                    prevLogTerm в prevLogIndex (т.Е. logIndex является preLogIndex для отсутствия
                    записи в журнале), верните false*/
                    if (logModule.getLast().getTerm() > param.getLastLogTerm()) {
                        return builder.term(node.getCurrentTerm()).voteGranted(false).build();
                    }
                    if (logModule.getLastIndex() > param.getLastLogIndex()) {
                        return builder.term(node.getCurrentTerm()).voteGranted(false).build();
                    }
                }
                node.lokMutex();
                node.setNodeStatusIndex(NodeStatus.FOLLOWER);
                peerSet.setLeader(new Peer(param.getCandidateId()));
                node.setCurrentTerm(param.getTerm());
                node.unlockMutex();
                return builder.term(node.getCurrentTerm()).voteGranted(true).build();
            }
            return builder.term(node.getCurrentTerm()).voteGranted(false).build();
        } finally {
            voteLock.unlock();
        }
    }

    @Override
    public AentryResult appendEntries(AentryParam param) {
        AentryResult result = AentryResult.fail();
        try {

            if (!appendLock.tryLock()) {
                return result;
            }

            node.lokMutex();
            //System.out.println(param);
            result.setTerm(node.getCurrentTerm());
            result.setLastApl(node.getLastApplied());

            //1
            if (param.getTerm() < node.getCurrentTerm()) {
                node.unlockMutex();
                return result;
            }
            electionTask.setPreElectionTime(System.currentTimeMillis());
            peerSet.setLeader(new Peer(param.getLeaderId()));
            if (param.getTerm() > node.getCurrentTerm()) {
                node.setNodeStatusIndex(NodeStatus.FOLLOWER);
            }
            node.setCurrentTerm(param.getTerm());

            if (param.getLeaderCommit() > node.getLastApplied() && logModule.read(node.getLastApplied()+1) != null) {
                long apply;
                do {
                    apply = node.getLastApplied() + 1;
                    logModule.applyToStateMachine(apply);
                    node.setLastApplied(apply);
                } while (apply<logModule.getLastIndex());
                result.setLastApl(node.getLastApplied());
                //node.unlockMutex();
                //return new AentryResult(node.getCurrentTerm(), node.getLastApplied(), true);
            }

            /*Если подписчик не найдет в своем журнале запись, содержащую ту же позицию
            в индексе и номер владения, он отклонит новую запись*/
            /*В алгоритме Raft лидер устраняет несоответствия, заставляя подписчика
            копировать свой журнал. Это означает, что записи журнала, конфликтующие
            с лидером, будут перезаписаны записями журнала лидера.*/
            if (logModule.getLastIndex() != 0 || param.getPrevLogIndex() != 0) {
                LogEntry logEntry;
                if ((logEntry = logModule.read(param.getPrevLogIndex())) != null) {
                    //2
                    if (logEntry.getTerm() != param.getPreLogTerm()) {
                        logModule.removeOnStartIndex(param.getPrevLogIndex() + 1);
                        node.unlockMutex();
                        return result;
                    }
                } else if(param.getPrevLogIndex() != 0){
                    node.unlockMutex();
                    return result;
                }
            }

            if (param.getLeaderCommit() > node.getLastApplied() && param.getEntries().length == 0) {
                node.unlockMutex();
                return result;
            }

            // записываем операцию в журнал логов
            for (LogEntry entry : param.getEntries()) {
                logModule.write(entry);
                result.setSuccess(true);
            }

            /*Если leaderCommit > commitIndex, установите commitIndex = min для Подписчика (leaderCommit, индекс последней новой записи)*/
            if (param.getLeaderCommit() > node.getCommitIndex()) {
                int commitIndex = (int) Math.min(param.getLeaderCommit(), logModule.getLastIndex());
                node.setCommitIndex(commitIndex);
            }
            result.setTerm(node.getCurrentTerm());
            result.setSuccess(true);
            node.unlockMutex();
            return result;
        } finally {
            appendLock.unlock();
        }
    }

}
