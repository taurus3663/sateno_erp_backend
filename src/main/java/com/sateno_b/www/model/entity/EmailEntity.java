package com.sateno_b.www.model.entity;

import com.sateno_b.www.model.enums.EmailType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Table;
import lombok.*;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "email")
public class EmailEntity extends BaseEntity {

    @Column
    private boolean active;
    @Column
    private String name;

//    IMAP/POP3
    @Column
    private String host;
    @Column
    private int port;
    @Column
    private String username;
    @Column
    private String password;
    @Column
    private EmailType emailType;
    @Column
    private boolean ssl;

//    SMTP
    @Column
    private String hostSmtp;
    @Column
    private String portSmtp;
    @Column
    private String usernameSmtp;
    @Column
    private String passwordSmtp;
    @Column
    private boolean sslSmtp;
    @Column
    private String signature;

}
