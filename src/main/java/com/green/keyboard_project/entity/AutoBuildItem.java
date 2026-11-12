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
@Table(name = "auto_build_item")
public class AutoBuildItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "auto_item_id", nullable = false)
    private int autoItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auto_build_id", nullable = false)
    private AutoBuild autoBuild;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "unit_price", nullable = false)
    private int unitPrice;

}
