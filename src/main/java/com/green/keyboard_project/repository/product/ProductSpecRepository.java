package com.green.keyboard_project.repository.product;

import com.green.keyboard_project.entity.ProductSpec;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductSpecRepository extends JpaRepository<ProductSpec, Integer> {
}
