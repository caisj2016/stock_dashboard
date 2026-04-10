package com.caisj.stockdashboard.backend.repository;

import com.caisj.stockdashboard.backend.domain.model.PortfolioItemRecord;
import java.util.List;

public interface PortfolioRepository {
    List<PortfolioItemRecord> findAll();

    List<PortfolioItemRecord> saveAll(List<PortfolioItemRecord> items);
}
