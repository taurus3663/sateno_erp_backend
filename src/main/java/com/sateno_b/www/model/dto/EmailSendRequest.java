package com.sateno_b.www.model.dto;

import com.sateno_b.www.model.entity.SiteEntity;
import lombok.Data;

@Data
public class EmailSendRequest {

    private Long configId;
    private String to;
    private String subject;
    private String content;
    private boolean genConfirm = false;
}
