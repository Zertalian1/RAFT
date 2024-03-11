package org.example.node.consensus;

import io.grpc.netty.shaded.io.netty.util.internal.StringUtil;
import org.example.log.entity.LogEntry;
import org.example.node.DefaultNode;
import org.example.node.NodeStatus;
import org.example.node.entity.AentryParam;
import org.example.node.entity.AentryResult;
import org.example.node.entity.RvoteParam;
import org.example.node.entity.RvoteResult;
import org.example.server.Peer;

import java.util.concurrent.locks.ReentrantLock;

public class DefaultConsensus implements Consensus{
    public final DefaultNode node;
    public final ReentrantLock voteLock = new ReentrantLock();
    public final ReentrantLock appendLock = new ReentrantLock();

    public DefaultConsensus(DefaultNode node) {
        this.node = node;
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
                if (node.getLogModule().getLast() != null) {
                    /*Если журнал отслеживания не содержит записи с термином, совпадающим с
                    prevLogTerm в prevLogIndex (т.Е. logIndex является preLogIndex для отсутствия
                    записи в журнале), верните false*/
                    if (node.getLogModule().getLast().getTerm() > param.getLastLogTerm()) {
                        return builder.term(node.getCurrentTerm()).voteGranted(false).build();
                    }
                    if (node.getLogModule().getLastIndex() > param.getLastLogIndex()) {
                        return builder.term(node.getCurrentTerm()).voteGranted(false).build();
                    }
                }
                node.setNodeStatusIndex(NodeStatus.FOLLOWER);
                node.getPeerSet().setLeader(new Peer(param.getCandidateId()));
                node.setCurrentTerm(param.getTerm());
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
            result.setTerm(node.getCurrentTerm());
            if (param.getTerm() < node.getCurrentTerm()) {
                return result;
            }
            node.setPreHeartBeatTime(System.currentTimeMillis());
            node.setPreElectionTime(System.currentTimeMillis());
            node.getPeerSet().setLeader(new Peer(param.getLeaderId()));
            if (param.getTerm() >= node.getCurrentTerm()) {
                node.setNodeStatusIndex(NodeStatus.FOLLOWER);
            }
            node.setCurrentTerm(param.getTerm());
            if (param.getEntries() == null || param.getEntries().length == 0) {
                return new AentryResult(node.getCurrentTerm(), true);
            }

            /*Если подписчик не найдет в своем журнале запись, содержащую ту же позицию
            в индексе и номер владения, он отклонит новую запись*/
            if (node.getLogModule().getLastIndex() != 0 && param.getPrevLogIndex() != 0) {
                LogEntry logEntry;
                if ((logEntry = node.getLogModule().read(param.getPrevLogIndex())) != null) {
                    if (logEntry.getTerm() != param.getPreLogTerm()) {
                        return result;
                    }
                } else {
                    return result;
                }

            }

            /*В алгоритме Raft лидер устраняет несоответствия, заставляя подписчика
            копировать свой журнал. Это означает, что записи журнала, конфликтующие
            с лидером, будут перезаписаны записями журнала лидера.*/
            LogEntry existLog = node.getLogModule().read(param.getPrevLogIndex() + 1);
            if (existLog != null && existLog.getTerm() != param.getEntries()[0].getTerm()) {
                // короче всё удаляем
                node.getLogModule().removeOnStartIndex(param.getPrevLogIndex() + 1);
            } else if (existLog != null) {
                result.setSuccess(true);
                return result;
            }

            // применяем операцию к конечному автомату и записываем в журнал логов
            for (LogEntry entry : param.getEntries()) {
                node.getLogModule().write(entry);
                node.stateMachine.apply(entry);
                result.setSuccess(true);
            }

            /*Если leaderCommit > commitIndex, установите commitIndex = min для Подписчика (leaderCommit, индекс последней новой записи)*/
            if (param.getLeaderCommit() > node.getCommitIndex()) {
                int commitIndex = (int) Math.min(param.getLeaderCommit(), node.getLogModule().getLastIndex());
                node.setCommitIndex(commitIndex);
                node.setLastApplied(commitIndex);
            }
            result.setTerm(node.getCurrentTerm());
            result.setSuccess(true);
            return result;
        } finally {
            appendLock.unlock();
        }
    }

}
