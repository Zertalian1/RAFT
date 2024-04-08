package org.example;

import org.example.distributedBlocking.CASReplDict;
import org.example.distributedBlocking.Lock;
import org.example.distributedBlocking.TtlTask;
import org.example.node.*;
import org.example.thread.RaftThreadPool;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
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
        Lock lock = new Lock("test", 4000L, "");
        Scanner scanner = new Scanner(System.in);
        while (true) {
            int command = scanner.nextInt();
            switch (command) {
                case 0 -> {
                    String uid = consumer.unlock(lock);
                    if (uid == null) {
                        System.out.println("Не получилось отпустить мьютекс");
                    } else {
                        System.out.println("Мьютекс отпущен uid="+uid);
                    }
                }
                case 1 -> {
                    String uid = consumer.lock(lock);
                    if (uid == null) {
                        System.out.println("Не получилось залочить мьютекс");
                    } else {
                        System.out.println("Мьютекс залочен uid="+uid);
                    }
                    lock.setVersion(uid);
                }
                case 2 -> {
                    String uid = consumer.renew(lock);
                    if (uid == null) {
                        System.out.println("Не получилось залочить мьютекс");
                    } else {
                        System.out.println("Мьютекс залочен uid="+uid);
                    }
                    lock.setVersion(uid);
                }
            }
        }
    }

    public static void main(String[] args) {
        List<String> peers = new ArrayList<>();
        for (int i=1;i<args.length;i++) {
            peers.add("localhost:"+args[i]);
        }
        NodeConfig config = new NodeConfig(Integer.parseInt(args[0]), peers);
        ReplDict consumer = new ReplDict();
        //CASReplDict consumer = new CASReplDict();
        DefaultNode node = new DefaultNode(config, consumer);
        node.init();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                node.destroy();
            } catch (Throwable ignored) {
            }
        }));
        /*TtlTask task = new TtlTask();
        task.setNode(node);
        task.setLogModule(node.getLogModule());
        task.setReplicationService(consumer.getReplicationService());
        CasKVStorage(consumer, task);*/
        defKVStorage(consumer);
    }
}