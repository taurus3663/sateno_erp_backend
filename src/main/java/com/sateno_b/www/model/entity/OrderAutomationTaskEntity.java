package com.sateno_b.www.model.entity;

import com.sateno_b.www.model.enums.TaskType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "order_automation_task")
@Data
public class OrderAutomationTaskEntity extends BaseEntity {


    @ManyToOne
    private WpOrderEntity order;

    @Column
    private Instant scheduledTime;

    @Enumerated(EnumType.STRING)
    private TaskType taskType;

    @Column
    private boolean processed = false;
}
