package com.green.keyboard_project.service;

import com.green.keyboard_project.dto.OrderCreateRequest;
import com.green.keyboard_project.dto.OrderItemRequest;
import com.green.keyboard_project.dto.OrderItemResponse;
import com.green.keyboard_project.dto.OrderResponse;
import com.green.keyboard_project.entity.Order;
import com.green.keyboard_project.entity.OrderItem;
import com.green.keyboard_project.entity.Product;
import com.green.keyboard_project.entity.User;
import com.green.keyboard_project.exceptions.InvalidStateException;
import com.green.keyboard_project.exceptions.ResourceNotFountException;
import com.green.keyboard_project.repository.OrderItemRepository;
import com.green.keyboard_project.repository.OrderRepository;
import com.green.keyboard_project.repository.ProductRepository;
import com.green.keyboard_project.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;         // 주문(Order) 데이터 접근 계층
    private final OrderItemRepository orderItemRepository; // 주문 상품(OrderItem) 데이터 접근 계층
    private final ProductRepository productRepository;     // 상품(Product) 조회 및 재고 갱신용
    private final UserRepository userRepository;           // 주문자 회원(User) 조회용

    /**
     * 주문 생성 (주문 및 주문 상품 일괄 저장)
     * - @Transactional: 중간에 예외 발생 시 모든 DB 작업을 롤백하여 데이터 정합성을 보장합니다.
     */
    @Transactional
    public OrderResponse createOrder(OrderCreateRequest request) {
        // [1] 주문 상품 목록 필수값 검증
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new InvalidStateException("주문 상품 목록이 비어 있습니다.");
        }

        // [2] 주문자 회원 조회 및 유효성 검증
        if (request.getOrdererEmail() == null || request.getOrdererEmail().isBlank()) {
            throw new InvalidStateException("주문자 이메일은 필수입니다.");
        }

        User user = userRepository.findByEmail(request.getOrdererEmail())
                .orElseThrow(() -> new ResourceNotFountException("등록된 회원 정보를 찾을 수 없습니다. (이메일: " + request.getOrdererEmail() + ")"));

        if (user.isDeleted()) {
            throw new InvalidStateException("탈퇴한 회원은 주문할 수 없습니다.");
        }

        // [3] 주문자 이름 및 연락처(DB 11자리 제한) 정제
        String ordererName = request.getOrdererName() != null && !request.getOrdererName().isBlank()
                ? request.getOrdererName()
                : user.getName();

        String rawPhone = request.getOrdererPhone() != null && !request.getOrdererPhone().isBlank()
                ? request.getOrdererPhone()
                : user.getPhoneNumber();
        String cleanPhone = rawPhone != null ? rawPhone.replaceAll("[^0-9]", "") : "";
        if (cleanPhone.length() > 11) {
            cleanPhone = cleanPhone.substring(0, 11); // orders 테이블 orderer_phone 컬럼 길이 11자리 준수
        } else if (cleanPhone.isEmpty()) {
            cleanPhone = "01000000000";
        }

        String ordererEmail = user.getEmail();

        // [4] 고유 주문번호 생성 (형식: ORD-yyMMddHHmmss-UUID6자리, DB 컬럼 32자 준수)
        String timePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmmss"));
        String uuidPart = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        String orderNumber = "ORD-" + timePart + "-" + uuidPart;

        // [5] 상품 재고 확인, 재고 차감 및 총 주문 금액 계산
        int totalProductAmount = 0;
        List<OrderItem> itemsToSave = new ArrayList<>();

        for (OrderItemRequest itemReq : request.getItems()) {
            // 상품 조회
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFountException("상품을 찾을 수 없습니다. (ID: " + itemReq.getProductId() + ")"));

            // 재고 수량 확인
            if (product.getStockQuantity() < itemReq.getQuantity()) {
                throw new InvalidStateException("상품 재고가 부족합니다. (" + product.getName() + ", 현재 재고: " + product.getStockQuantity() + "개)");
            }

            // 재고 차감 반영 (JPA Dirty Checking으로 트랜잭션 종료 시 자동 UPDATE)
            product.setStockQuantity(product.getStockQuantity() - itemReq.getQuantity());

            // 단가 및 소계 계산
            int lineAmount = product.getBasePrice() * itemReq.getQuantity();
            totalProductAmount += lineAmount;

            // 주문 품목 엔티티 생성 (주문 당시의 상품명, 단가를 스냅샷으로 보존)
            OrderItem orderItem = new OrderItem();
            orderItem.setProduct(product);
            orderItem.setProductNameSnapshot(product.getName());
            orderItem.setOrderedPrice(product.getBasePrice());
            orderItem.setQuantity(itemReq.getQuantity());
            itemsToSave.add(orderItem);
        }

        int deliveryFee = 0; // 배송비 정책 (기본 0원)
        int finalPayAmount = totalProductAmount + deliveryFee;

        // [6] 주문(Order) 엔티티 저장
        Order order = new Order();
        order.setOrderNumber(orderNumber);
        order.setUser(user);
        order.setOrdererName(ordererName);
        order.setOrdererPhone(cleanPhone);
        order.setOrdererEmail(ordererEmail);
        order.setOrderStatus(Order.OrderStatusType.ORDERED);
        order.setTotalProductAmount(totalProductAmount);
        order.setDeliveryFee(deliveryFee);
        order.setFinalPayAmount(finalPayAmount);
        order.setOrderedAt(LocalDateTime.now());
        order.setCreatedAt(LocalDateTime.now());

        Order savedOrder = orderRepository.save(order);

        // [7] 주문 상품(OrderItem) 목록에 생성된 주문(Order)을 연결하여 저장
        for (OrderItem orderItem : itemsToSave) {
            orderItem.setOrder(savedOrder);
            orderItemRepository.save(orderItem);
        }

        // [8] 저장 완료된 엔티티들을 응답 DTO로 변환하여 반환
        return toOrderResponse(savedOrder, itemsToSave);
    }

    /**
     * 전체 주문 목록 최신 등록순 조회
     */
    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        List<Order> orders = orderRepository.findAllByOrderByCreatedAtDesc();
        List<OrderResponse> responses = new ArrayList<>();

        for (Order order : orders) {
            List<OrderItem> items = orderItemRepository.findByOrder(order);
            responses.add(toOrderResponse(order, items));
        }

        return responses;
    }

    /**
     * 주문 단건 조회 (주문 ID 기준)
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(int orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFountException("주문을 찾을 수 없습니다. (ID: " + orderId + ")"));

        List<OrderItem> items = orderItemRepository.findByOrder(order);
        return toOrderResponse(order, items);
    }

    /**
     * 주문 단건 조회 (주문번호 기준)
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrderByOrderNumber(String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFountException("주문을 찾을 수 없습니다. (주문번호: " + orderNumber + ")"));

        List<OrderItem> items = orderItemRepository.findByOrder(order);
        return toOrderResponse(order, items);
    }

    /**
     * 엔티티(Order, OrderItem) -> 클라이언트 응답 DTO(OrderResponse) 변환 헬퍼 메서드
     */
    private OrderResponse toOrderResponse(Order order, List<OrderItem> items) {
        List<OrderItemResponse> itemResponses = new ArrayList<>();
        if (items != null) {
            for (OrderItem item : items) {
                itemResponses.add(OrderItemResponse.builder()
                        .orderItemId(item.getOrderItemId())
                        .productId(item.getProduct() != null ? item.getProduct().getProductId() : null)
                        .productName(item.getProductNameSnapshot())
                        .unitPrice(item.getOrderedPrice())
                        .quantity(item.getQuantity())
                        .totalPrice(item.getOrderedPrice() * item.getQuantity())
                        .build());
            }
        }

        return OrderResponse.builder()
                .orderId(order.getOrderId())
                .orderNumber(order.getOrderNumber())
                .orderStatus(order.getOrderStatus().name())
                .ordererName(order.getOrdererName())
                .ordererPhone(order.getOrdererPhone())
                .ordererEmail(order.getOrdererEmail())
                .totalProductAmount(order.getTotalProductAmount())
                .deliveryFee(order.getDeliveryFee())
                .finalPayAmount(order.getFinalPayAmount())
                .orderedAt(order.getOrderedAt())
                .createdAt(order.getCreatedAt())
                .items(itemResponses)
                .build();
    }
}
