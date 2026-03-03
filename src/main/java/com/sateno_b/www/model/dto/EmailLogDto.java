package com.sateno_b.www.model.dto;

import com.sateno_b.www.model.enums.EmailDirection;
import lombok.Data;

import java.time.Instant;

@Data
public class EmailLogDto {

    private Long id;
    private String sender;
    private String recipient;
    private String subject;
    private String body;
    private boolean success;
    private String errorMessage;
    private EmailDto config;
    private EmailDirection direction;

    private boolean seen;
    private boolean confirmed;
    private Instant createTime;

}
