package com.sateno_b.www.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.Instant;
import java.util.List;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public class NekorektenResponseDto {
        private List<NekorektenItemDto> items;
        private int count;
        private String message;

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class NekorektenItemDto {
            private String id;
            private String firstName;
            private String lastName;
            private String phone;
            private String text; // Това е описанието на некоректното поведение
            private Instant createDate;
        }
    }


