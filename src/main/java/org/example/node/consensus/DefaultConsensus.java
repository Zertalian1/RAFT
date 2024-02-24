package org.example.node.consensus;

import io.grpc.netty.shaded.io.netty.util.internal.StringUtil;
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
            if (param.getTerm() < node.getCurrentTerm()) {
                return builder.term(node.getCurrentTerm()).voteGranted(false).build();
            }
            if ((StringUtil.isNullOrEmpty(node.getVotedFor()) || node.getVotedFor().equals(param.getCandidateId()))) {
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
            result.setTerm(node.getCurrentTerm());
            result.setSuccess(true);
            return result;
        } finally {
            appendLock.unlock();
        }
    }

}
