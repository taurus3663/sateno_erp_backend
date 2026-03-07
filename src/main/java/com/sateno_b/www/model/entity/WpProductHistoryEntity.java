package com.sateno_b.www.model.entity;

import com.sateno_b.www.model.listeners.WpOrderEntityListener;
import jakarta.persistence.*;
import lombok.*;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "wp_product_history")
public class WpProductHistoryEntity extends BaseEntity {

    @ManyToOne(fetch =  FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private WpProductEntity product;

    @Column
    private Integer quantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private WpOrderEntity order;

    @Column
    private String reason;
}
