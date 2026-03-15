package com.sateno_b.www.model.dto;

import com.sateno_b.www.model.entity.data.Dimensions;
import com.sateno_b.www.model.enums.ProductStatus;
import lombok.Data;

import java.util.List;

@Data
public class WooProductDto {

    private ProductStatus status;
    private Long id; // product id on wp site
    private String name;
    private String slug;
    private String sku;
    private String type;
    private String description;
    private String short_description;
    private String price;
    private String regular_price;
    private String sale_price;
    private String weight;
    private Integer stock_quantity;
    private List<WooProductImageDto> images;
    private List<WooProductCategoryDto> categories;
    private List<WooBrandDto> brands;
    private List<WooAddonDto> addons;
    private Dimensions dimensions;
    private boolean manage_stock;
    private String stock_status;
    private boolean featured;
    private String catalog_visibility;

}
