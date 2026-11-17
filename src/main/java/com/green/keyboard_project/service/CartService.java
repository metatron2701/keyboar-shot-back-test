package com.green.keyboard_project.service;

import com.green.keyboard_project.dto.cart.CartItemResponse;
import com.green.keyboard_project.entity.CartItem;
import com.green.keyboard_project.entity.Product;
import com.green.keyboard_project.entity.User;
import com.green.keyboard_project.exceptions.InvalidStateException;
import com.green.keyboard_project.exceptions.ResourceNotFountException;
import com.green.keyboard_project.repository.cart.CartItemRepository;
import com.green.keyboard_project.repository.ProductRepository;
import com.green.keyboard_project.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CartService {
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    /**
     * 회원의 장바구니 목록 조회
     */
    @Transactional(readOnly = true)
    public List<CartItemResponse> getCartItems(int userId) {
        List<CartItem> items = cartItemRepository.findByUser_UserId(userId);
        return items.stream().map(item -> {
            Product product = item.getProduct();
            return CartItemResponse.builder()
                    .cartItemId(item.getCartItemId())
                    .userId(userId)
                    .productId(product != null ? product.getProductId() : 0)
                    .name(product != null ? product.getName() : "")
                    .price(product != null ? product.getBasePrice() : 0)
                    .stockQuantity(product != null ? product.getStockQuantity() : 0)
                    .quantity(item.getQuantity())
                    .thumbnailUrl(product != null ? product.getThumbnailUrl() : "")
                    .build();
        }).toList();
    }

    /**
     * 장바구니 상품 추가
     */
    @Transactional
    public void addCartItem(int userId, int productId, int quantity) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFountException("사용자를 찾을 수 없습니다. (ID: " + userId + ")"));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFountException("상품을 찾을 수 없습니다. (ID: " + productId + ")"));

        // 🛡️ 신규/기존 공통: 0 이하이거나 단건 수량이 재고수를 초과하면 즉시 거부
        if (quantity <= 0 || quantity > product.getStockQuantity()) {
            throw new InvalidStateException("유효하지 않은 상품 갯수 입니다.");
        }

        Optional<CartItem> existingItem = cartItemRepository.findByUserAndProduct(user, product);

        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();
            // 🛡️ 기존 수량과의 합산이 재고수를 초과해도 거부
            if (item.getQuantity() + quantity > product.getStockQuantity()) {
                throw new InvalidStateException("유효하지 않은 상품 갯수 입니다.");
            }
            item.setQuantity(item.getQuantity() + quantity);
            item.setUpdatedAt(LocalDateTime.now());
        } else {
            CartItem newItem = new CartItem();
            newItem.setUser(user);
            newItem.setProduct(product);
            newItem.setQuantity(quantity);
            newItem.setCreatedAt(LocalDateTime.now());
            newItem.setUpdatedAt(LocalDateTime.now());

            cartItemRepository.save(newItem);
        }
    }

    /**
     * 장바구니 상품 수량 변경 (PATCH)
     */
    @Transactional
    public void updateCartItemQuantity(int userId, int productId, int quantity) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFountException("사용자를 찾을 수 없습니다. (ID: " + userId + ")"));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFountException("상품을 찾을 수 없습니다. (ID: " + productId + ")"));

        if (quantity <= 0 || quantity > product.getStockQuantity()) {
            throw new InvalidStateException("유효하지 않은 상품 갯수 입니다.");
        }

        Optional<CartItem> existingItem = cartItemRepository.findByUserAndProduct(user, product);
        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();
            item.setQuantity(quantity);
            item.setUpdatedAt(LocalDateTime.now());
        }
    }

    /**
     * 장바구니 상품 단건 삭제 (DELETE)
     */
    @Transactional
    public void removeCartItem(int userId, int productId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFountException("사용자를 찾을 수 없습니다. (ID: " + userId + ")"));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFountException("상품을 찾을 수 없습니다. (ID: " + productId + ")"));
        cartItemRepository.findByUserAndProduct(user, product)
                .ifPresent(cartItemRepository::delete);
    }

    /**
     * 장바구니 전체 비우기 (DELETE)
     */
    @Transactional
    public void clearCart(int userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFountException("사용자를 찾을 수 없습니다. (ID: " + userId + ")"));
        cartItemRepository.deleteByUser(user);
    }
}
