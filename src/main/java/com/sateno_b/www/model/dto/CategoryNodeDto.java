package com.sateno_b.www.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
public class CategoryNodeDto {
    private NodeData data;
    private boolean leaf;
    private List<CategoryNodeDto> children = new ArrayList<>();

    @Data
    @AllArgsConstructor
    @NoArgsConstructor // Добави това за Jackson
    public static class NodeData {
        private Long id;
        private String name;
        private String slug;
        private Long parentId;      // ID на родителя
        private String parentName;  // Име на родителя (за таблицата/lookup)
    }
}