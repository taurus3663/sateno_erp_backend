package com.sateno_b.www.shared;

import java.util.Base64;

public class AuthTool {

    public static String getAuth(String key, String secret) {
        return Base64.getEncoder().encodeToString((key + ":" + secret).getBytes());
    }
}
