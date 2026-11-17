package com.green.keyboard_project.controller;

import com.green.keyboard_project.dto.ApiResponse;
import com.green.keyboard_project.dto.cart.CartAddRequest;
import com.green.keyboard_project.dto.cart.CartItemResponse;
import com.green.keyboard_project.dto.cart.CartQuantityUpdateRequest;
import com.green.keyboard_project.exceptions.InvalidStateException;
import com.green.keyboard_project.exceptions.ResourceNotFoundException;
import com.green.keyboard_project.exceptions.UnauthenticatedException;
import com.green.keyboard_project.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {
    private final CartService cartService;

    /**
     * 1. 내 장바구니 목록 조회 (GET /api/cart)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<CartItemResponse>>> getMyCart(@AuthenticationPrincipal Integer userId) {
        if (userId == null) {
            throw new UnauthenticatedException("로그인이 필요합니다.");
        }
        List<CartItemResponse> items = cartService.getCartItems(userId);
        return ResponseEntity.ok(ApiResponse.ok(items));
    }

    /**
     * 2. 장바구니 상품 추가 (POST /api/cart)
     */
    @PostMapping
    public ResponseEntity<ApiResponse<String>> addCartItem(
            @AuthenticationPrincipal Integer userId,
            @RequestBody CartAddRequest request
    ) {
        if (userId == null) {
            throw new UnauthenticatedException("로그인이 필요합니다.");
        }
        if (request.getProductId() == null) {
            throw new ResourceNotFoundException("존재하지 않는 상품입니다.");
        }
        if (request.getQuantity() == null) {
            throw new InvalidStateException("유효하지 않은 상품 갯수 입니다.");
        }

        cartService.addCartItem(userId, request.getProductId(), request.getQuantity());
        return ResponseEntity.ok(ApiResponse.ok("장바구니에 추가되었습니다."));
    }

    /**
     * 3. 장바구니 수량 변경 (PATCH /api/cart/{productId})
     */
    @PatchMapping("/{productId}")
    public ResponseEntity<ApiResponse<String>> updateCartItemQuantity(
            @AuthenticationPrincipal Integer userId,
            @PathVariable int productId,
            @RequestBody CartQuantityUpdateRequest request
    ) {
        if (userId == null) {
            throw new UnauthenticatedException("로그인이 필요합니다.");
        }
        int quantity = request.getQuantity() != null ? request.getQuantity() : 1;
        cartService.updateCartItemQuantity(userId, productId, quantity);
        return ResponseEntity.ok(ApiResponse.ok("수량이 변경되었습니다."));
    }

    /**
     * 4. 장바구니 상품 단건 삭제 (DELETE /api/cart/{productId})
     */
    @DeleteMapping("/{productId}")
    public ResponseEntity<ApiResponse<String>> removeCartItem(
            @AuthenticationPrincipal Integer userId,
            @PathVariable int productId
    ) {
        if (userId == null) {
            throw new UnauthenticatedException("로그인이 필요합니다.");
        }
        cartService.removeCartItem(userId, productId);
        return ResponseEntity.ok(ApiResponse.ok("장바구니에서 삭제되었습니다."));
    }

    /**
     * 5. 장바구니 전체 비우기 (DELETE /api/cart)
     */
    @DeleteMapping
    public ResponseEntity<ApiResponse<String>> clearCart(
            @AuthenticationPrincipal Integer userId
    ) {
        if (userId == null) {
            throw new UnauthenticatedException("로그인이 필요합니다.");
        }
        cartService.clearCart(userId);
        return ResponseEntity.ok(ApiResponse.ok("장바구니가 비워졌습니다."));
    }
}
