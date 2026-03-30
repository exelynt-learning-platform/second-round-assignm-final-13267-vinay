package com.ecommerce.app.controller;

import com.ecommerce.app.dto.request.CartItemRequest;
import com.ecommerce.app.dto.response.CartResponse;
import com.ecommerce.app.models.User;
import com.ecommerce.app.services.CartService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    // GET /api/cart
    // Returns the authenticated user's cart with all items and computed total
    @GetMapping
    public ResponseEntity<CartResponse> getCart(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(cartService.getCart(currentUser.getId()));
    }

    // POST /api/cart/items
    // Add a product to the cart (or increase quantity if already present)
    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CartItemRequest request) {
        return ResponseEntity.ok(cartService.addItem(currentUser.getId(), request));
    }

    // PUT /api/cart/items/{productId}?quantity=3
    // Update the quantity of a specific product in the cart
    // quantity=0 removes the item
    @PutMapping("/items/{productId}")
    public ResponseEntity<CartResponse> updateItem(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long productId,
            @RequestParam @Min(value = 0, message = "Quantity must be 0 or greater") int quantity) {
        return ResponseEntity.ok(cartService.updateItem(currentUser.getId(), productId, quantity));
    }

    // DELETE /api/cart/items/{productId}
    // Remove a specific product from the cart
    @DeleteMapping("/items/{productId}")
    public ResponseEntity<CartResponse> removeItem(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long productId) {
        return ResponseEntity.ok(cartService.removeItem(currentUser.getId(), productId));
    }

    // DELETE /api/cart
    // Clear all items from the cart
    @DeleteMapping
    public ResponseEntity<Void> clearCart(
            @AuthenticationPrincipal User currentUser) {
        cartService.clearCart(currentUser.getId());
        return ResponseEntity.noContent().build();
    }
}