package com.ecommerce.app.servicesImpl;

import com.ecommerce.app.dto.request.ProductRequest;
import com.ecommerce.app.dto.response.ProductResponse;
import com.ecommerce.app.exception.ResourceNotFoundException;
import com.ecommerce.app.models.Product;
import com.ecommerce.app.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductServiceImpl")
class ProductServiceImplTest {

    @Mock ProductRepository productRepository;

    @InjectMocks ProductServiceImpl productService;

    private Product  sampleProduct;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        sampleProduct = Product.builder()
                .id(1L)
                .name("Wireless Mouse")
                .description("Ergonomic wireless mouse")
                .price(new BigDecimal("29.99"))
                .stockQuantity(100)
                .imageUrl("https://cdn.example.com/mouse.jpg")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        pageable = PageRequest.of(0, 10);
    }

    // createProduct()
    @Nested
    @DisplayName("createProduct()")
    class CreateProduct {

        @Test
        @DisplayName("should persist product and return mapped response")
        void success_returnsProductResponse() {
            ProductRequest req = buildRequest(
                    "Wireless Mouse", "Ergonomic wireless mouse",
                    new BigDecimal("29.99"), 100, "https://cdn.example.com/mouse.jpg");

            when(productRepository.save(any(Product.class))).thenReturn(sampleProduct);

            ProductResponse res = productService.createProduct(req);

            assertThat(res.getId()).isEqualTo(1L);
            assertThat(res.getName()).isEqualTo("Wireless Mouse");
            assertThat(res.getPrice()).isEqualByComparingTo(new BigDecimal("29.99"));
            assertThat(res.getStockQuantity()).isEqualTo(100);

            verify(productRepository, times(1)).save(any(Product.class));
        }

        @Test
        @DisplayName("should map all request fields to entity before saving")
        void success_allFieldsMapped() {
            ProductRequest req = buildRequest(
                    "Keyboard", "Mechanical keyboard",
                    new BigDecimal("79.99"), 50, "https://cdn.example.com/kb.jpg");

            Product savedProduct = Product.builder()
                    .id(2L).name("Keyboard").description("Mechanical keyboard")
                    .price(new BigDecimal("79.99")).stockQuantity(50)
                    .imageUrl("https://cdn.example.com/kb.jpg")
                    .build();

            when(productRepository.save(any(Product.class))).thenReturn(savedProduct);

            ProductResponse res = productService.createProduct(req);

            assertThat(res.getName()).isEqualTo("Keyboard");
            assertThat(res.getDescription()).isEqualTo("Mechanical keyboard");
            assertThat(res.getImageUrl()).isEqualTo("https://cdn.example.com/kb.jpg");
        }
    }

    // getProductById()
    @Nested
    @DisplayName("getProductById()")
    class GetProductById {

        @Test
        @DisplayName("should return ProductResponse when product exists")
        void found_returnsResponse() {
            when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));

            ProductResponse res = productService.getProductById(1L);

            assertThat(res.getId()).isEqualTo(1L);
            assertThat(res.getName()).isEqualTo("Wireless Mouse");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when product does not exist")
        void notFound_throwsException() {
            when(productRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.getProductById(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Product")
                    .hasMessageContaining("999");
        }
    }

    // getAllProducts()
    @Nested
    @DisplayName("getAllProducts()")
    class GetAllProducts {

        @Test
        @DisplayName("should return paginated results when no search term provided")
        void noSearch_returnsAllPaginated() {
            Page<Product> page = new PageImpl<>(List.of(sampleProduct), pageable, 1);
            when(productRepository.findAll(pageable)).thenReturn(page);

            Page<ProductResponse> result = productService.getAllProducts(null, pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("Wireless Mouse");
        }

        @Test
        @DisplayName("should use search query when search term is provided")
        void withSearch_callsSearchRepository() {
            Page<Product> page = new PageImpl<>(List.of(sampleProduct), pageable, 1);
            when(productRepository
                    .findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
                            "mouse", "mouse", pageable))
                    .thenReturn(page);

            Page<ProductResponse> result = productService.getAllProducts("mouse", pageable);

            assertThat(result.getContent()).hasSize(1);
            verify(productRepository)
                    .findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
                            "mouse", "mouse", pageable);
            verify(productRepository, never()).findAll(any(Pageable.class));
        }

        @Test
        @DisplayName("should return empty page when no products match search")
        void withSearch_noResults_returnsEmptyPage() {
            Page<Product> emptyPage = new PageImpl<>(List.of(), pageable, 0);
            when(productRepository
                    .findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
                            anyString(), anyString(), any(Pageable.class)))
                    .thenReturn(emptyPage);

            Page<ProductResponse> result = productService.getAllProducts("xyz123", pageable);

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }
    }

    // updateProduct()
    @Nested
    @DisplayName("updateProduct()")
    class UpdateProduct {

        @Test
        @DisplayName("should update all fields and return updated response")
        void success_updatesFields() {
            ProductRequest req = buildRequest(
                    "Updated Mouse", "New description",
                    new BigDecimal("39.99"), 80, "https://cdn.example.com/new.jpg");

            Product updated = Product.builder()
                    .id(1L).name("Updated Mouse").description("New description")
                    .price(new BigDecimal("39.99")).stockQuantity(80)
                    .imageUrl("https://cdn.example.com/new.jpg")
                    .build();

            when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
            when(productRepository.save(any(Product.class))).thenReturn(updated);

            ProductResponse res = productService.updateProduct(1L, req);

            assertThat(res.getName()).isEqualTo("Updated Mouse");
            assertThat(res.getPrice()).isEqualByComparingTo(new BigDecimal("39.99"));
            assertThat(res.getStockQuantity()).isEqualTo(80);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when product does not exist")
        void notFound_throwsException() {
            when(productRepository.findById(999L)).thenReturn(Optional.empty());

            ProductRequest req = buildRequest("X", "Y",
                    BigDecimal.TEN, 1, null);

            assertThatThrownBy(() -> productService.updateProduct(999L, req))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(productRepository, never()).save(any());
        }
    }

    // deleteProduct()
    @Nested
    @DisplayName("deleteProduct()")
    class DeleteProduct {

        @Test
        @DisplayName("should call delete on repository when product exists")
        void success_callsDelete() {
            when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
            doNothing().when(productRepository).delete(sampleProduct);

            productService.deleteProduct(1L);

            verify(productRepository, times(1)).delete(sampleProduct);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when product does not exist")
        void notFound_throwsException() {
            when(productRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.deleteProduct(999L))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(productRepository, never()).delete(any());
        }
    }

    // ── Builder ───────────────────────────────────────────────────────────────
    private ProductRequest buildRequest(String name, String desc,
                                        BigDecimal price, int stock, String imageUrl) {
        ProductRequest r = new ProductRequest();
        r.setName(name); r.setDescription(desc);
        r.setPrice(price); r.setStockQuantity(stock);
        r.setImageUrl(imageUrl);
        return r;
    }
}