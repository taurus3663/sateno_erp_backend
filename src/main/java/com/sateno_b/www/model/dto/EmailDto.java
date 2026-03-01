package com.sateno_b.www.model.dto;

import com.sateno_b.www.model.enums.EmailType;
import lombok.Data;

@Data
public class EmailDto {

    private Long id;
    private boolean active;
    private String name;

    //    IMAP/POP3
    private String host;
    private int port;
    private String username;
    private String password;
    private EmailType emailType;
    private boolean ssl;

    //    SMTP
    private String hostSmtp;
    private int portSmtp;
    private String usernameSmtp;
    private String passwordSmtp;
    private boolean sslSmtp;

    private String signature;

}
