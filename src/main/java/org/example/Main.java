package org.example;

import org.example.distributedBlocking.CASReplDict;
import org.example.distributedBlocking.Client;
import org.example.distributedBlocking.Lock;
import org.example.distributedBlocking.TtlTask;
import org.example.node.*;
import org.example.thread.RaftThreadPool;

import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    public static void defKVStorage(ReplDict consumer) {
        final AtomicInteger counter = new AtomicInteger(0);
        while (true) {
            Scanner scanner = new Scanner(System.in);
            int command = scanner.nextInt();
            switch (command) {
                case 0 -> {
                    if (consumer.get("test") != null) {
                        counter.set(Integer.parseInt(consumer.get("test")));
                    }
                    consumer.set("test", String.valueOf(counter.incrementAndGet()));
                }
                case 1 -> System.out.println(consumer.get("test"));
            }
        }
    }

    public static void CasKVStorage(CASReplDict consumer, TtlTask task) {
        RaftThreadPool.scheduleWithFixedDelay(task, 300);
        Queue<Integer> queue = new ArrayDeque<>();
        Semaphore mutex = new Semaphore(1);
        Client client1 = new Client(queue, 0, consumer);
        Client client2 = new Client(queue, 3, consumer);
        RaftThreadPool.execute(client1);
        RaftThreadPool.execute(client2);
        Scanner scanner = new Scanner(System.in);
        while (true) {
            Integer comand = scanner.nextInt();
            queue.add(comand);
        }
    }

    public static void main(String[] args) {
        List<String> peers = new ArrayList<>();
        for (int i=1;i<args.length;i++) {
            peers.add("localhost:"+args[i]);
        }
        NodeConfig config = new NodeConfig(Integer.parseInt(args[0]), peers);
        //ReplDict consumer = new ReplDict();
        CASReplDict consumer = new CASReplDict();
        DefaultNode node = new DefaultNode(config, consumer);
        node.init();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                node.destroy();
            } catch (Throwable ignored) {
            }
        }));
        TtlTask task = new TtlTask();
        task.setNode(node);
        task.setLogModule(node.getLogModule());
        task.setReplicationService(consumer.getReplicationService());
        CasKVStorage(consumer, task);
        //defKVStorage(consumer);
    }
}