package org.example.node;

import org.example.node.entity.RvoteParam;
import org.example.node.entity.RvoteResult;

public interface Node {
    void setConfig(NodeConfig config);
    void init();
    void destroy();
    RvoteResult handlerRequestVote(RvoteParam param);
}
