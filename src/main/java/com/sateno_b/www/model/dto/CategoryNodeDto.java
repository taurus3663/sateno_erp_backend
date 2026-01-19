package com.sateno_b.www.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CategoryNodeDto {
    private NodeData data;
    private boolean leaf; // Ако е true, няма стрелка в Angular
    private List<CategoryNodeDto> children = new ArrayList<>();

    @Data
    @AllArgsConstructor
    public static class NodeData {
        private Long id;
        private String name;
        private String slug;
//        private Long wpId; // WordPress ID-то от мапинга
    }
}