package org.example.node.entity;

import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class RvoteParam implements Serializable {
    private String candidateId;
    private long term;
    long lastLogIndex;
    long lastLogTerm;
}
