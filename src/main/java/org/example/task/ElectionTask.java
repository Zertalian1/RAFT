package org.example.task;

import org.example.node.DefaultNode;
import org.example.node.NodeStatus;
import org.example.node.entity.RvoteParam;
import org.example.node.entity.RvoteResult;
import org.example.rpc.entity.Command;
import org.example.rpc.entity.RaftRemotingException;
import org.example.rpc.entity.Request;
import org.example.rpc.entity.Response;
import org.example.server.Peer;
import org.example.thread.RaftThreadPool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class ElectionTask implements Runnable {

    private final DefaultNode node;

    public ElectionTask(DefaultNode node) {
        this.node = node;
    }

    @Override
    public void run() {

        if (node.getNodeStatus() == NodeStatus.LEADER) {
            return;
        }

        long current = System.currentTimeMillis();

        node.setElectionTime(node.getElectionTime() + ThreadLocalRandom.current().nextInt(50));
        if (current - node.getPreElectionTime() < node.getElectionTime()) {
            return;
        }
        node.setNodeStatusIndex(NodeStatus.CANDIDATE);
        node.setPreElectionTime(System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(200)+150);
        node.setVotedFor("localhost:" + node.getPort());

        ArrayList<Future<Response<RvoteResult>>> futureArrayList = new ArrayList<>();
        node.newTerm();
        for (int i = 0 ; i< node.getPeerSet().getPeers().size(); i++) {
            Peer peer = node.getPeerSet().getPeers().get(i);
            futureArrayList.add(RaftThreadPool.submit(() -> {
                RvoteParam param = new RvoteParam();
                param.setTerm(node.getCurrentTerm());
                param.setCandidateId(node.getVotedFor());
                System.out.println("Голосуем за себя:   "+ param+" терм   "+ param.getTerm());
                Request<RvoteParam> request = new Request<>(Command.R_VOTE.ordinal(), param, peer.getAddr());
                try {
                    return node.getRpcClient().send(request);
                } catch (RaftRemotingException e) {
                    return null;
                }
            }));
        }

        AtomicInteger success2 = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(futureArrayList.size());

        for (Future<Response<RvoteResult>> future : futureArrayList) {
            RaftThreadPool.submit(() -> {
                try {
                    Response<RvoteResult> response = future.get(3000, MILLISECONDS);
                    if (response == null) {
                        return -1;
                    }
                    boolean isVoteGranted = response.getResult().isVoteGranted();

                    if (isVoteGranted) {
                        success2.incrementAndGet();
                    } else {
                        long resTerm = response.getResult().getTerm();
                        if (resTerm >= node.getCurrentTerm()) {
                            node.setCurrentTerm(resTerm);
                        }
                    }
                    return 0;
                } catch (Exception e) {
                    return -1;
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await(3500, MILLISECONDS);
        } catch (InterruptedException ignored) {
        }

        int success = success2.get();
        if (node.getNodeStatus() == NodeStatus.FOLLOWER) {
            return;
        }
        if (success >= node.getPeerSet().getPeers().size() / 2) {
            node.setNodeStatusIndex(NodeStatus.LEADER);
            node.getPeerSet().setLeader(new Peer("localhost:" + node.getPort()));
        }
        node.setVotedFor("");
    }
}
