package org.example.node.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.example.log.entity.LogEntry;
import org.example.server.Peer;

import java.util.concurrent.Callable;

@AllArgsConstructor
@Builder
@Getter
@Setter
public class ReplicationFailModel {
    private String countKey;
    private String successKey;
    private Callable callable;
    private LogEntry logEntry;
    private Peer peer;
    private Long offerTime;
}
