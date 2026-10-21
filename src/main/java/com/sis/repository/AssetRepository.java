package com.sis.repository;

import com.sis.entity.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AssetRepository extends JpaRepository<Asset, Long> {
    List<Asset> findByActiveTrueOrderByNameAsc();
}