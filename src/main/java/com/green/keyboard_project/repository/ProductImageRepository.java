package com.green.keyboard_project.repository;

import com.green.keyboard_project.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, Integer> {

    List<ProductImage> findByProduct_ProductIdOrderByDisplayOrderAsc(int productId);

    List<ProductImage> findByProduct_ProductIdInOrderByDisplayOrderAsc(List<Integer> productIds);
}
