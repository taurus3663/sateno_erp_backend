package com.sateno_b.www.model.entity;

import jakarta.persistence.*;
import lombok.*;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "discount_phones")
public class DiscountPhone extends BaseEntity {

    @Column(nullable = false)
    private String phoneNumber;

    @ManyToOne
    @JoinColumn(name = "site_id", referencedColumnName = "id")
    private SiteEntity site;

    @OneToOne
    @JoinColumn(name = "customer_id")
    private CustomerEntity customer;
}
