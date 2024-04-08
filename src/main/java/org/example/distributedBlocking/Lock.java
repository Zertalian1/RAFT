package org.example.distributedBlocking;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class Lock {
    private String name;
    private Long ttl;
    private String version;
}
