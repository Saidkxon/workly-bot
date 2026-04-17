package com.advancedprogramming.worklybot.entity;

import com.advancedprogramming.worklybot.entity.enums.Role;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "employees")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_user_id", nullable = false, unique = true)
    private Long telegramUserId;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "department", nullable = false)
    private String department;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "active", nullable = false)
    private boolean active;
}
