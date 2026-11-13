package com.green.keyboard_project.repository;

import com.green.keyboard_project.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 상품(Product) 데이터 접근 리포지토리
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Integer> {

    // 판매 중인 활성 상품(isActive = true) 목록을 페이징하여 조회
    Page<Product> findByIsActiveTrue(Pageable pageable);
}
