package org.example;

import org.example.node.*;

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

    public static void CasKVStorage(CASReplDict consumer) {
        final AtomicInteger counter = new AtomicInteger(0);
        while (true) {
            Scanner scanner = new Scanner(System.in);
            int command = scanner.nextInt();
            switch (command) {
                case 0 -> {
                    if (consumer.get("test") != null) {
                        counter.set(Integer.parseInt(consumer.get("test")));
                    }
                    int value = counter.incrementAndGet();
                    int prev_value = value-1;
                    consumer.set("test", String.valueOf(value), String.valueOf(prev_value));
                }
                case 1 -> System.out.println(consumer.get("test"));
            }
        }
    }
    public static void main(String[] args) {
        //String[] peerAddr = {"localhost:8080","localhost:8082"};
        List<String> peers = new ArrayList<>();
        for (int i=1;i<args.length;i++) {
            peers.add("localhost:"+args[i]);
        }
        NodeConfig config = new NodeConfig(Integer.parseInt(args[0]), peers);
        //ReplDict consumer = new ReplDict();
        CASReplDict consumer = new CASReplDict();
        Node node = new DefaultNode(config, consumer);
        node.init();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                node.destroy();
            } catch (Throwable ignored) {
            }
        }));
        CasKVStorage(consumer);
    }
}