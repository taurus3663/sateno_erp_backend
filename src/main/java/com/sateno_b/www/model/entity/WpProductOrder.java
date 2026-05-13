package com.sateno_b.www.model.entity;

import com.sateno_b.www.model.converter.LongListConverter;
import com.sateno_b.www.model.listeners.WpProductEntityListener;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@EntityListeners(WpProductEntityListener.class)
@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "wp_product_order")
public class WpProductOrder extends BaseEntity {

    @Column(columnDefinition = "TEXT")
    @Convert(converter = LongListConverter.class)
    private List<Long> productIds = new ArrayList<>();

    @OneToOne
    private WpCategoryEntity category;
}
