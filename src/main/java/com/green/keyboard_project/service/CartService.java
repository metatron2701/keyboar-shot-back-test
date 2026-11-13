package com.green.keyboard_project.service;

import com.green.keyboard_project.dto.CartItemResponse;
import com.green.keyboard_project.entity.CartItem;
import com.green.keyboard_project.entity.Product;
import com.green.keyboard_project.entity.User;
import com.green.keyboard_project.repository.CartItemRepository;
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
        User user = userRepository.findById(userId).orElseThrow();
        Product product = productRepository.findById(productId).orElseThrow();

        Optional<CartItem> existingItem = cartItemRepository.findByUserAndProduct(user, product);

        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();
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
        User user = userRepository.findById(userId).orElseThrow();
        Product product = productRepository.findById(productId).orElseThrow();
        Optional<CartItem> existingItem = cartItemRepository.findByUserAndProduct(user, product);
        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();
            item.setQuantity(Math.max(1, quantity));
            item.setUpdatedAt(LocalDateTime.now());
        }
    }

    /**
     * 장바구니 상품 단건 삭제 (DELETE)
     */
    @Transactional
    public void removeCartItem(int userId, int productId) {
        User user = userRepository.findById(userId).orElseThrow();
        Product product = productRepository.findById(productId).orElseThrow();
        cartItemRepository.findByUserAndProduct(user, product)
                .ifPresent(cartItemRepository::delete);
    }

    /**
     * 장바구니 전체 비우기 (DELETE)
     */
    @Transactional
    public void clearCart(int userId) {
        User user = userRepository.findById(userId).orElseThrow();
        cartItemRepository.deleteByUser(user);
    }
}
