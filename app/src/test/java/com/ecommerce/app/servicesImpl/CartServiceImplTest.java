package com.ecommerce.app.servicesImpl;

import com.ecommerce.app.dto.request.CartItemRequest;
import com.ecommerce.app.dto.response.CartResponse;
import com.ecommerce.app.exception.BadRequestException;
import com.ecommerce.app.exception.ResourceNotFoundException;
import com.ecommerce.app.models.Cart;
import com.ecommerce.app.models.CartItem;
import com.ecommerce.app.models.Product;
import com.ecommerce.app.models.User;
import com.ecommerce.app.repository.CartItemRepository;
import com.ecommerce.app.repository.CartRepository;
import com.ecommerce.app.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CartServiceImpl")
class CartServiceImplTest {

    @Mock CartRepository     cartRepository;
    @Mock CartItemRepository cartItemRepository;
    @Mock ProductRepository  productRepository;

    @InjectMocks CartServiceImpl cartService;

    private User    user;
    private Cart    cart;
    private Product product;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L).email("alice@example.com").build();

        cart = Cart.builder()
                .id(10L).user(user)
                .cartItems(new ArrayList<>())
                .build();

        product = Product.builder()
                .id(5L).name("Laptop")
                .price(new BigDecimal("999.99"))
                .stockQuantity(10)
                .build();
    }

    // getCart()
    @Nested
    @DisplayName("getCart()")
    class GetCart {

        @Test
        @DisplayName("should return cart with total = 0.00 for empty cart")
        void emptyCart_returnsZeroTotal() {
            when(cartRepository.findByUserIdWithItems(1L)).thenReturn(Optional.of(cart));

            CartResponse res = cartService.getCart(1L);

            assertThat(res.getCartId()).isEqualTo(10L);
            assertThat(res.getItems()).isEmpty();
            assertThat(res.getTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("should compute total correctly from item prices and quantities")
        void cartWithItems_computesTotalCorrectly() {
            CartItem item = CartItem.builder()
                    .id(1L).cart(cart).product(product).quantity(3).build();
            cart.getCartItems().add(item);

            when(cartRepository.findByUserIdWithItems(1L)).thenReturn(Optional.of(cart));

            CartResponse res = cartService.getCart(1L);

            // 999.99 × 3 = 2999.97
            assertThat(res.getTotal()).isEqualByComparingTo(new BigDecimal("2999.97"));
            assertThat(res.getItems()).hasSize(1);
            assertThat(res.getItems().get(0).getQuantity()).isEqualTo(3);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when cart not found")
        void cartNotFound_throws() {
            when(cartRepository.findByUserIdWithItems(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.getCart(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Cart");
        }
    }

    // addItem()
    @Nested
    @DisplayName("addItem()")
    class AddItem {

        @Test
        @DisplayName("should create new CartItem when product not yet in cart")
        void newProduct_createsCartItem() {
            CartItemRequest req = buildRequest(5L, 2);

            when(cartRepository.findByUserIdWithItems(1L))
                    .thenReturn(Optional.of(cart))
                    .thenReturn(Optional.of(cartWithItem(product, 2)));
            when(productRepository.findById(5L)).thenReturn(Optional.of(product));
            when(cartItemRepository.findByCartIdAndProductId(10L, 5L))
                    .thenReturn(Optional.empty());
            when(cartRepository.save(any(Cart.class))).thenReturn(cart);

            CartResponse res = cartService.addItem(1L, req);

            assertThat(res.getItems()).hasSize(1);
            assertThat(res.getItems().get(0).getQuantity()).isEqualTo(2);
            assertThat(res.getTotal())
                    .isEqualByComparingTo(new BigDecimal("1999.98")); // 999.99 × 2
        }

        @Test
        @DisplayName("should increase quantity when product already in cart")
        void existingProduct_increasesQuantity() {
            CartItem existing = CartItem.builder()
                    .id(1L).cart(cart).product(product).quantity(1).build();

            CartItemRequest req = buildRequest(5L, 2);

            when(cartRepository.findByUserIdWithItems(1L))
                    .thenReturn(Optional.of(cart))
                    .thenReturn(Optional.of(cartWithItem(product, 3)));
            when(productRepository.findById(5L)).thenReturn(Optional.of(product));
            when(cartItemRepository.findByCartIdAndProductId(10L, 5L))
                    .thenReturn(Optional.of(existing));
            when(cartRepository.save(any(Cart.class))).thenReturn(cart);

            CartResponse res = cartService.addItem(1L, req);

            // existing 1 + added 2 = 3
            assertThat(res.getItems().get(0).getQuantity()).isEqualTo(3);
        }

        @Test
        @DisplayName("should throw BadRequestException when requested qty exceeds stock")
        void insufficientStock_throws() {
            CartItemRequest req = buildRequest(5L, 50); // stock is only 10

            when(cartRepository.findByUserIdWithItems(1L)).thenReturn(Optional.of(cart));
            when(productRepository.findById(5L)).thenReturn(Optional.of(product));

            assertThatThrownBy(() -> cartService.addItem(1L, req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Only 10 units available");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when product not in catalog")
        void productNotFound_throws() {
            CartItemRequest req = buildRequest(999L, 1);

            when(cartRepository.findByUserIdWithItems(1L)).thenReturn(Optional.of(cart));
            when(productRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.addItem(1L, req))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Product");
        }
    }

    // updateItem()
    @Nested
    @DisplayName("updateItem()")
    class UpdateItem {

        @Test
        @DisplayName("should update quantity when new qty is valid and in stock")
        void success_updatesQuantity() {
            CartItem item = CartItem.builder()
                    .id(1L).cart(cart).product(product).quantity(2).build();

            when(cartRepository.findByUserIdWithItems(1L)).thenReturn(Optional.of(cart));
            when(cartItemRepository.findByCartIdAndProductId(10L, 5L))
                    .thenReturn(Optional.of(item));
            when(cartItemRepository.save(any(CartItem.class))).thenReturn(item);
            when(cartRepository.findByUserIdWithItems(1L))
                    .thenReturn(Optional.of(cart))
                    .thenReturn(Optional.of(cartWithItem(product, 5)));

            CartResponse res = cartService.updateItem(1L, 5L, 5);

            assertThat(res.getItems().get(0).getQuantity()).isEqualTo(5);
        }

        @Test
        @DisplayName("should remove item when quantity is set to 0")
        void quantityZero_removesItem() {
            when(cartRepository.findByUserIdWithItems(1L))
                    .thenReturn(Optional.of(cart));
            doNothing().when(cartItemRepository)
                    .deleteByCartIdAndProductId(10L, 5L);

            CartResponse res = cartService.updateItem(1L, 5L, 0);

            verify(cartItemRepository).deleteByCartIdAndProductId(10L, 5L);
            assertThat(res.getItems()).isEmpty();
        }

        @Test
        @DisplayName("should throw BadRequestException when new qty exceeds stock")
        void exceedsStock_throws() {
            CartItem item = CartItem.builder()
                    .id(1L).cart(cart).product(product).quantity(2).build();

            when(cartRepository.findByUserIdWithItems(1L)).thenReturn(Optional.of(cart));
            when(cartItemRepository.findByCartIdAndProductId(10L, 5L))
                    .thenReturn(Optional.of(item));

            assertThatThrownBy(() -> cartService.updateItem(1L, 5L, 999))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Only 10 units available");
        }
    }

    // removeItem()
    @Nested
    @DisplayName("removeItem()")
    class RemoveItem {

        @Test
        @DisplayName("should call deleteByCartIdAndProductId and return updated cart")
        void success_removesItem() {
            when(cartRepository.findByUserIdWithItems(1L)).thenReturn(Optional.of(cart));
            doNothing().when(cartItemRepository)
                    .deleteByCartIdAndProductId(10L, 5L);

            CartResponse res = cartService.removeItem(1L, 5L);

            verify(cartItemRepository).deleteByCartIdAndProductId(10L, 5L);
            assertThat(res.getItems()).isEmpty();
        }
    }

    // clearCart()
    @Nested
    @DisplayName("clearCart()")
    class ClearCart {

        @Test
        @DisplayName("should clear all items and save the empty cart")
        void success_clearsAllItems() {
            cart.getCartItems().add(CartItem.builder()
                    .id(1L).cart(cart).product(product).quantity(2).build());

            when(cartRepository.findByUserIdWithItems(1L)).thenReturn(Optional.of(cart));
            when(cartRepository.save(any(Cart.class))).thenReturn(cart);

            cartService.clearCart(1L);

            assertThat(cart.getCartItems()).isEmpty();
            verify(cartRepository).save(cart);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private CartItemRequest buildRequest(Long productId, int qty) {
        CartItemRequest r = new CartItemRequest();
        r.setProductId(productId); r.setQuantity(qty);
        return r;
    }

    private Cart cartWithItem(Product p, int qty) {
        CartItem item = CartItem.builder()
                .id(1L).cart(cart).product(p).quantity(qty).build();
        Cart c = Cart.builder()
                .id(10L).user(user)
                .cartItems(new ArrayList<>(List.of(item)))
                .build();
        return c;
    }
}