package com.sateno_b.www.model.dto;

import lombok.Data;

@Data
public class WhatsAppRequestDTO {
    private String systemId;
    private String password;
    private String destination; // Номерът на клиента (напр. 359888123456)
    private String text;        // Съобщението
    private String sender;      // Твоят senderID: 15559474049
    private String channel = "WHATSAPP"; // Указваме канала
}
