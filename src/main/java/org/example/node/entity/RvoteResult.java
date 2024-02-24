package org.example.node.entity;

import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RvoteResult implements Serializable {
    long term;
    boolean voteGranted;
}
