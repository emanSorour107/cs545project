package cs545_project.online_market.service;

import cs545_project.online_market.controller.request.ProductRequest;
import cs545_project.online_market.domain.Product;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.ArrayList;


public interface ProductService {
    void test();
    public void saveProduct(ProductRequest productRequest, String path);
    public ArrayList<Product> getAll();
    public Product findById(Long productId);
    public void deleteProduct(Long productId);
    public void updateProduct(ProductRequest productRequest, String path, Long id);

}
