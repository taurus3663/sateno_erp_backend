package com.sateno_b.www.model.dto;

import lombok.Data;

@Data
public class EmailSendRequest {

    private Long configId;
    private String to;
    private String subject;
    private String content;
}
