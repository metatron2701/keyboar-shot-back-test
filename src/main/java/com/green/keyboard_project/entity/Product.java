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
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id", nullable = false)
    private int productId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "base_price", nullable = false)
    private int basePrice;

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    @Column(name = "thumbnail_url", nullable = true, length = 500)
    private String thumbnailUrl;

    @Column(name = "description", nullable = true, columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "version", nullable = false)
    @Version
    private int version;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;

    @Column(name = "deleted_at", nullable = true)
    private LocalDateTime deletedAt;

    /**
     * 카테고리 계층 구조(소분류 -> 대분류)를 기반으로 부품 구분을 동적 계산
     */
    public String getDerivedPartType() {
        if (category == null) return null;
        String parentId = (category.getParent() != null) 
                ? category.getParent().getCategoryId() 
                : category.getCategoryId();
        if (parentId == null) return null;
        
        String lower = parentId.toLowerCase();
        if (lower.contains("hous")) return "HOUSING";
        if (lower.contains("switch")) return "SWITCH";
        if (lower.contains("keycap")) return "KEYCAP";
        return "PCB"; // keyboards, pcb
    }
}