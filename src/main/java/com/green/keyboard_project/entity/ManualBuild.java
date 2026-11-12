package com.green.keyboard_project.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "manual_build")
public class ManualBuild {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "manual_build_id", nullable = false)
    private int manualBuildId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    private User user;

    @Column(name = "build_name", nullable = false, length = 100)
    private String buildName;

    @Column(name = "include_assembly", nullable = false)
    private boolean includeAssembly;

    @Column(name = "assembly_fee", nullable = false)
    private int assemblyFee;

    @Column(name = "total_price", nullable = false)
    private int totalPrice;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

}
