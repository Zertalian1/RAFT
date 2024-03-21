package org.example.node;

public interface Node {
    void setConfig(NodeConfig config, SyncObjConsumer consumer);
    void init();
    void destroy();
}
