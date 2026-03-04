package com.sateno_b.www.model.entity;

import com.sateno_b.www.model.enums.EmailDirection;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "email_log")
public class EmailLogEntity extends BaseEntity {

    @Column
    private String sender;
    @Column
    private String recipient;
    @Column
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String body;
    @Column
    private boolean success;
    @Column
    private String errorMessage;

    @ManyToOne
    private EmailEntity config;

    @Enumerated(EnumType.STRING)
    private EmailDirection direction;

    @Column(columnDefinition = "TEXT")
    private String privateSeenKey;

    @Column(columnDefinition = "TEXT")
    private String privateConfirmKey;

    @Column
    private boolean seen = false;

    @Column
    private boolean confirmed = false;

    @Column
    private boolean cancel = false;

}
