package com.green.keyboard_project.repository;

import com.green.keyboard_project.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Integer> {

    @Query("SELECT p FROM Product p WHERE p.isDeleted = false AND p.isActive = true AND p.stockQuantity > 0 AND " +
           "(:category IS NULL OR :category = 'all' OR p.category.categoryId = :category OR (p.category.parent IS NOT NULL AND p.category.parent.categoryId = :category)) AND " +
           "(:subcategory IS NULL OR p.category.categoryId = :subcategory)")
    Page<Product> findFilteredProducts(@Param("category") String category,
                                       @Param("subcategory") String subcategory,
                                       Pageable pageable);
}
