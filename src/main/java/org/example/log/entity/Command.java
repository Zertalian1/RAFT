package org.example.log.entity;

import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class Command implements Serializable {
    private String name;
    private String[] params;
}
