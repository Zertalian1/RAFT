package org.example.node.entity;

import lombok.*;
import org.example.log.entity.LogEntry;
import org.example.server.Peer;

import java.io.Serializable;
import java.util.concurrent.Callable;

@AllArgsConstructor
@Builder
@ToString
@Getter
@Setter
public class ReplicationFailModel implements Serializable {
    private int countKey;
    private String successKey;
    private Callable callable;
    private LogEntry logEntry;
    private Peer peer;
}
