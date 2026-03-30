package com.ecommerce.app.servicesImpl;

import com.ecommerce.app.dto.request.CartItemRequest;
import com.ecommerce.app.dto.response.CartResponse;
import com.ecommerce.app.models.Cart;
import com.ecommerce.app.models.CartItem;
import com.ecommerce.app.models.Product;
import com.ecommerce.app.exception.BadRequestException;
import com.ecommerce.app.exception.ResourceNotFoundException;
import com.ecommerce.app.repository.CartItemRepository;
import com.ecommerce.app.repository.CartRepository;
import com.ecommerce.app.repository.ProductRepository;
import com.ecommerce.app.services.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository     cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository  productRepository;

    // Get cart (with items) for a user
    @Override
    @Transactional(readOnly = true)
    public CartResponse getCart(Long userId) {
        Cart cart = findCartByUser(userId);
        return toResponse(cart);
    }

    // Add item — if product already in cart, increase quantity
    @Override
    @Transactional
    public CartResponse addItem(Long userId, CartItemRequest request) {
        Cart    cart    = findCartByUser(userId);
        Product product = findProduct(request.getProductId());

        if (product.getStockQuantity() < request.getQuantity()) {
            throw new BadRequestException(
                    "Only " + product.getStockQuantity() + " units available for '" + product.getName() + "'");
        }

        cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId())
                .ifPresentOrElse(
                        existing -> existing.setQuantity(existing.getQuantity() + request.getQuantity()),
                        () -> {
                            CartItem newItem = CartItem.builder()
                                    .cart(cart)
                                    .product(product)
                                    .quantity(request.getQuantity())
                                    .build();
                            cart.getCartItems().add(newItem);
                        }
                );

        cartRepository.save(cart);
        return toResponse(findCartByUser(userId));       // re-fetch with updated items
    }

    // Update quantity — quantity = 0 removes the item
    @Override
    @Transactional
    public CartResponse updateItem(Long userId, Long productId, int quantity) {
        Cart cart = findCartByUser(userId);

        if (quantity <= 0) {
            return removeItem(userId, productId);
        }

        CartItem item = cartItemRepository
                .findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", "productId", productId));

        Product product = item.getProduct();
        if (product.getStockQuantity() < quantity) {
            throw new BadRequestException(
                    "Only " + product.getStockQuantity() + " units available for '" + product.getName() + "'");
        }

        item.setQuantity(quantity);
        cartItemRepository.save(item);
        return toResponse(findCartByUser(userId));
    }

    // Remove a single product from the cart
    @Override
    @Transactional
    public CartResponse removeItem(Long userId, Long productId) {
        Cart cart = findCartByUser(userId);
        cartItemRepository.deleteByCartIdAndProductId(cart.getId(), productId);
        return toResponse(findCartByUser(userId));
    }

    // Clear entire cart (called after order is placed)
    @Override
    @Transactional
    public void clearCart(Long userId) {
        Cart cart = findCartByUser(userId);
        cart.getCartItems().clear();
        cartRepository.save(cart);
    }

    // Helpers
    private Cart findCartByUser(Long userId) {
        return cartRepository.findByUserIdWithItems(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart", "userId", userId));
    }

    private Product findProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));
    }

    private CartResponse toResponse(Cart cart) {
        List<CartResponse.CartItemResponse> itemResponses = cart.getCartItems().stream()
                .map(item -> CartResponse.CartItemResponse.builder()
                        .cartItemId(item.getId())
                        .productId(item.getProduct().getId())
                        .productName(item.getProduct().getName())
                        .imageUrl(item.getProduct().getImageUrl())
                        .unitPrice(item.getProduct().getPrice())
                        .quantity(item.getQuantity())
                        .subtotal(item.getProduct().getPrice()
                                .multiply(BigDecimal.valueOf(item.getQuantity())))
                        .build())
                .toList();

        BigDecimal total = itemResponses.stream()
                .map(CartResponse.CartItemResponse::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CartResponse.builder()
                .cartId(cart.getId())
                .items(itemResponses)
                .total(total)
                .build();
    }
}