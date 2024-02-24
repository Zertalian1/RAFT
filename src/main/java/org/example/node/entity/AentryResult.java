package org.example.node.entity;

import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class AentryResult  implements Serializable {
    long term;
    boolean success;

    public static AentryResult fail() {
        return new AentryResult(0, false);
    }

    public static AentryResult ok() {
        return new AentryResult(0,true);
    }
}
