package com.java.repository;

import com.java.model.entity.BhProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BhProductRepository extends JpaRepository<BhProduct, Long> {

    Optional<BhProduct> findByBarcode(String barcode);

    @Query("SELECT p FROM BhProduct p WHERE p.barcode IN :barcodes")
    List<BhProduct> findByBarcodeIn(@Param("barcodes") List<String> barcodes);
}
