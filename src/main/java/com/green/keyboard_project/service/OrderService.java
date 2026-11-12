package com.green.keyboard_project.service;

import com.green.keyboard_project.dto.OrderCreateRequest;
import com.green.keyboard_project.dto.OrderCreateResponse;
import com.green.keyboard_project.repository.OrderRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    // 상품, 옵션, 주문 상품 Repository도 주입

    public OrderCreateResponse createOrder(
            OrderCreateRequest request
    ) {

        // 1. 요청 상품 목록이 비어 있는지 확인

        // 2. 상품 ID로 DB에서 상품 조회

        // 3. 옵션이 해당 상품에 속하는지 확인

        // 4. 재고 확인

        // 5. DB의 상품 가격과 옵션 추가 금액으로 총액 계산

        // 6. Order Entity 생성

        // 7. OrderItem Entity 생성 및 주문과 연결

        // 8. 주문과 주문 상품 저장

        // 9. 결제에 필요한 정보 반환

        return null;
    }
}