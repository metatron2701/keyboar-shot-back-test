package com.green.keyboard_project.repository;

import com.green.keyboard_project.entity.Order;
import com.green.keyboard_project.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 주문 상품(OrderItem) 데이터 접근 리포지토리
 */
@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Integer> {

    // 특정 주문(Order)에 속한 주문 상품 목록 조회
    List<OrderItem> findByOrder(Order order);
}
