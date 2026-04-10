package com.caisj.stockdashboard.backend.service.impl;

import com.caisj.stockdashboard.backend.domain.model.PortfolioItemRecord;
import com.caisj.stockdashboard.backend.dto.request.AddStockRequest;
import com.caisj.stockdashboard.backend.dto.request.RemoveStockRequest;
import com.caisj.stockdashboard.backend.dto.response.PortfolioResponse;
import com.caisj.stockdashboard.backend.exception.ApiException;
import com.caisj.stockdashboard.backend.repository.PortfolioRepository;
import com.caisj.stockdashboard.backend.service.PortfolioService;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class PortfolioServiceImpl implements PortfolioService {

    private static final List<String> ALLOWED_MARKER_COLORS = List.of("red", "blue", "green", "yellow", "purple", "cyan", "pink", "");

    private final PortfolioRepository portfolioRepository;

    public PortfolioServiceImpl(PortfolioRepository portfolioRepository) {
        this.portfolioRepository = portfolioRepository;
    }

    @Override
    public PortfolioResponse getPortfolio() {
        return new PortfolioResponse(
            portfolioRepository.findAll().stream()
                .map(this::toItem)
                .collect(Collectors.toList())
        );
    }

    @Override
    public PortfolioResponse updatePortfolio(List<PortfolioResponse.PortfolioItem> items) {
        List<PortfolioItemRecord> normalized = normalizeItems(items);
        portfolioRepository.saveAll(normalized);
        return new PortfolioResponse(normalized.stream().map(this::toItem).toList());
    }

    @Override
    public void addStock(AddStockRequest request) {
        String code = normalizeCode(request.code());
        if (code.isBlank()) {
            throw new ApiException("VALIDATION_ERROR", "missing code", HttpStatus.BAD_REQUEST);
        }

        List<PortfolioItemRecord> current = portfolioRepository.findAll();
        boolean exists = current.stream().anyMatch(item -> item.code().equalsIgnoreCase(code));
        if (exists) {
            throw new ApiException("ALREADY_EXISTS", "already exists", HttpStatus.CONFLICT);
        }

        List<PortfolioItemRecord> updated = new java.util.ArrayList<>(current);
        updated.add(new PortfolioItemRecord(code, defaultName(request.name(), code), 0.0, 0.0, "watch", ""));
        portfolioRepository.saveAll(updated);
    }

    @Override
    public void removeStock(RemoveStockRequest request) {
        String code = normalizeCode(request.code());
        List<PortfolioItemRecord> updated = portfolioRepository.findAll().stream()
            .filter(item -> !item.code().equalsIgnoreCase(code))
            .toList();
        portfolioRepository.saveAll(updated);
    }

    private PortfolioResponse.PortfolioItem toItem(PortfolioItemRecord item) {
        return new PortfolioResponse.PortfolioItem(
            item.code(),
            item.name(),
            item.shares() == null ? 0 : item.shares().intValue(),
            item.cost(),
            item.status(),
            item.markerColor()
        );
    }

    private List<PortfolioItemRecord> normalizeItems(List<PortfolioResponse.PortfolioItem> items) {
        List<PortfolioItemRecord> normalized = (items == null ? List.<PortfolioResponse.PortfolioItem>of() : items).stream()
            .map(this::normalizeItem)
            .filter(item -> !item.code().isBlank())
            .toList();

        if (normalized.isEmpty()) {
            return portfolioRepository.findAll();
        }
        return normalized;
    }

    private PortfolioItemRecord normalizeItem(PortfolioResponse.PortfolioItem item) {
        String code = normalizeCode(item.symbol());
        int shares = item.shares() == null ? 0 : Math.max(item.shares(), 0);
        double cost = item.cost() == null ? 0.0 : Math.max(item.cost(), 0.0);
        String status = normalizeStatus(item.status(), shares);
        String markerColor = normalizeMarkerColor(item.markerColor());

        return new PortfolioItemRecord(
            code,
            defaultName(item.name(), code),
            status.equals("holding") ? (double) shares : 0.0,
            status.equals("holding") ? cost : 0.0,
            status,
            markerColor
        );
    }

    private String normalizeCode(String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase();
        if (code.isEmpty()) {
            return "";
        }
        return code.endsWith(".T") ? code : code + ".T";
    }

    private String defaultName(String name, String code) {
        String value = name == null ? "" : name.trim();
        return value.isEmpty() ? code : value;
    }

    private String normalizeStatus(String rawStatus, int shares) {
        if ("holding".equalsIgnoreCase(rawStatus) || "watch".equalsIgnoreCase(rawStatus)) {
            return rawStatus.toLowerCase();
        }
        return shares > 0 ? "holding" : "watch";
    }

    private String normalizeMarkerColor(String rawMarkerColor) {
        String value = rawMarkerColor == null ? "" : rawMarkerColor.trim().toLowerCase();
        return ALLOWED_MARKER_COLORS.contains(value) ? value : "";
    }
}
