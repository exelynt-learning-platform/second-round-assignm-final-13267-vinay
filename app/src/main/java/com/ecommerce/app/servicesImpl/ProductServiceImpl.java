package com.ecommerce.app.servicesImpl;

import com.ecommerce.app.dto.request.ProductRequest;
import com.ecommerce.app.dto.response.ProductResponse;
import com.ecommerce.app.models.Product;
import com.ecommerce.app.exception.ResourceNotFoundException;
import com.ecommerce.app.repository.ProductRepository;
import com.ecommerce.app.services.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;

    // Create
    @Override
    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .stockQuantity(request.getStockQuantity())
                .imageUrl(request.getImageUrl())
                .build();

        return toResponse(productRepository.save(product));
    }

    // Read one
    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        return toResponse(findById(id));
    }

    // Read all (with optional keyword search, paginated)
    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> getAllProducts(String search, Pageable pageable) {
        if (StringUtils.hasText(search)) {
            return productRepository
                    .findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
                            search, search, pageable)
                    .map(this::toResponse);
        }
        return productRepository.findAll(pageable).map(this::toResponse);
    }

    // Update
    @Override
    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        Product product = findById(id);

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStockQuantity());
        product.setImageUrl(request.getImageUrl());

        return toResponse(productRepository.save(product));
    }

    // Delete
    @Override
    @Transactional
    public void deleteProduct(Long id) {
        Product product = findById(id);
        productRepository.delete(product);
    }

    // Helpers
    private Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
    }

    private ProductResponse toResponse(Product p) {
        return ProductResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .price(p.getPrice())
                .stockQuantity(p.getStockQuantity())
                .imageUrl(p.getImageUrl())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}