package com.green.keyboard_project.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "manual_build_item")
public class ManualBuildItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "manual_item_id", nullable = false)
    private int manualItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manual_build_id", nullable = false)
    private ManualBuild manualBuild;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "unit_price", nullable = false)
    private int unitPrice;

}
