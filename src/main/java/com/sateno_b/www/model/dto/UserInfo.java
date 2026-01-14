package com.sateno_b.www.model.dto;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class UserInfo {

    private Long id;
    private String username;
    private String firstName;
    private String lastName;
    private String middleName;
    private String email;
    private String phone;
    private String img;
}
