package com.ember.coordinator.entities;

import com.ember.coordinator.enums.Model;
import com.ember.coordinator.enums.TaskStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "tasks")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    private Model model;

    private String input;

    @Enumerated(EnumType.STRING)
    private TaskStatus status = TaskStatus.PENDING;
}
