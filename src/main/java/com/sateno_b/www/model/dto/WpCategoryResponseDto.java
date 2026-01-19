package com.sateno_b.www.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true) // Това е важно, за да не гърми, ако WP върне повече данни
public class WpCategoryResponseDto {
    private Long id;       // ID в WordPress
    private String name;   // Име на категорията
    private String slug;   // Слаг
    private Long parent;   // ID на родителя (0 ако е главна)
    private Integer count; // Брой продукти (полезно за статистика)
    private Integer menuOrder;
    private String displayType;
}
