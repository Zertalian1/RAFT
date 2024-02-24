package org.example.node.entity;

import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
public class BaseParam implements Serializable {
    private long term;
}
