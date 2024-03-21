package org.example.node.entity;

import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class RvoteParam implements Serializable {
    String candidateId;
    long term;
    long lastLogIndex;
    long lastLogTerm;
}
