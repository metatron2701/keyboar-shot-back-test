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
@Table(name = "auto_build")
public class AutoBuild {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "auto_build_id", nullable = false)
    private int autoBuildId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    private User user;

    @Column(name = "build_name", nullable = false, length = 100)
    private String buildName;

    @Column(name = "survey_layout", nullable = false, length = 20)
    private String surveyLayout;

    @Column(name = "survey_connectivity", nullable = false, length = 20)
    private String surveyConnectivity;

    @Column(name = "survey_sound", nullable = false, length = 20)
    private String surveySound;

    @Column(name = "survey_keycap", nullable = false, length = 20)
    private String surveyKeycap;

    @Column(name = "include_assembly", nullable = false)
    private boolean includeAssembly;

    @Column(name = "assembly_fee", nullable = false)
    private int assemblyFee;

    @Column(name = "total_price", nullable = false)
    private int totalPrice;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

}
