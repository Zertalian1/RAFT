package org.example.node.consensus;

import org.example.node.entity.AentryParam;
import org.example.node.entity.AentryResult;
import org.example.node.entity.RvoteParam;
import org.example.node.entity.RvoteResult;

public interface Consensus {
    RvoteResult requestVote(RvoteParam param);
    AentryResult appendEntries(AentryParam param);
}
