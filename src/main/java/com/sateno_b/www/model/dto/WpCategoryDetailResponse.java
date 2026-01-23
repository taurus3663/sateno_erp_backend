package com.sateno_b.www.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class WpCategoryDetailResponse {
    private String name;        // Името на категорията за избрания език
    private Long parentId;      // ID на родителя
    private String parentName;  // Име на родителя (за да се попълни автоматично в инпута)
}
