package com.tabish.SpringBootLab.repository;

import com.tabish.SpringBootLab.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Integer> {
}