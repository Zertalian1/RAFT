package org.example.node.entity;

import lombok.*;
import org.example.log.entity.LogEntry;

import java.io.Serializable;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class AentryParam implements Serializable {
    String leaderId;
    long term;
    long prevLogIndex;
    long preLogTerm;
    LogEntry[] entries;
    long leaderCommit;
}
