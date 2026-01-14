package com.sateno_b.www.security;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class) // Хваща АБСОЛЮТНО ВСИЧКИ грешки
    public ResponseEntity<Object> handleAllExceptions(Exception ex, WebRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("error", ex.getClass().getSimpleName()); // Име на грешката (напр. NullPointerException)

        // Взимаме най-дълбоката причина (истинския проблем от Postgres или Java)
        Throwable cause = ex;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        body.put("message", cause.getMessage()); // РЕАЛНАТА ГРЕШКА

        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
