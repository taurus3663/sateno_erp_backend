package com.sateno_b.www.model.dto;

import com.sateno_b.www.model.entity.CustomerEntity;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Data;

import java.time.Instant;

@Data
public class UserSignalDto {

//    private CustomerEntity customer;
    private String text;
    private String firstName;
    private String lastName;
    private Instant createDate;
}
