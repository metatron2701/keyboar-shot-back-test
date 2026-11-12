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
@Table(name = "product_spec")
public class ProductSpec {

    @Id
    @Column(name = "product_id", nullable = false)
    private int productId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "layout", nullable = true, length = 20)
    private String layout;

    @Column(name = "housing_material", nullable = true, length = 50)
    private String housingMaterial;

    @Column(name = "support_pins", nullable = true, length = 20)
    private String supportPins;

    @Column(name = "is_wireless", nullable = true)
    private Boolean isWireless;

    @Column(name = "switch_pin", nullable = true)
    private Integer switchPin;

    @Column(name = "switch_type", nullable = true, length = 50)
    private String switchType;

    @Column(name = "keycap_material", nullable = true, length = 50)
    private String keycapMaterial;

}
