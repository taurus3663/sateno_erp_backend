package com.sateno_b.www.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Data
@Entity
@Table(name = "app_settings")
public class AppSettingsEntity extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String type;

    @Column(nullable = false)
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> config;
}
