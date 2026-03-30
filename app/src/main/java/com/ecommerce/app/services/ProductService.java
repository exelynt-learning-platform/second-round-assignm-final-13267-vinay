package com.ecommerce.app.services;

import com.ecommerce.app.dto.request.ProductRequest;
import com.ecommerce.app.dto.response.ProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductService {
    ProductResponse  createProduct(ProductRequest request);
    ProductResponse  getProductById(Long id);
    Page<ProductResponse> getAllProducts(String search, Pageable pageable);
    ProductResponse  updateProduct(Long id, ProductRequest request);
    void             deleteProduct(Long id);
}