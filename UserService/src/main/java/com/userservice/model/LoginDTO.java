package com.userservice.model;

import lombok.Data;

@Data
public class LoginDTO {


    private String usernameOrEmail;

    private String password;
}
