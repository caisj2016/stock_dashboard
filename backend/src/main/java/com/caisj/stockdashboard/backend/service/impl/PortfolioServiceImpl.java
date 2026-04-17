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

/**
 * PortfolioService 的默认实现。
 * 负责持仓数据验证、标准化并与本地仓库交互进行读取与保存。
 */
@Service
public class PortfolioServiceImpl implements PortfolioService {

    private static final List<String> ALLOWED_MARKER_COLORS = List.of("red", "blue", "green", "yellow", "purple", "cyan", "pink", "");

    private final PortfolioRepository portfolioRepository;

    public PortfolioServiceImpl(PortfolioRepository portfolioRepository) {
        this.portfolioRepository = portfolioRepository;
    }

    /**
     * 从仓库读取组合持仓，并转换成前端可展示的组合响应结构。
     */
    @Override
    public PortfolioResponse getPortfolio() {
        return new PortfolioResponse(
            portfolioRepository.findAll().stream()
                .map(this::toItem)
                .collect(Collectors.toList())
        );
    }

    /**
     * 将用户提交的持仓列表标准化后保存为当前组合数据。
     * 如果传入列表为空，则保持原有持仓不变。
     */
    @Override
    public PortfolioResponse updatePortfolio(List<PortfolioResponse.PortfolioItem> items) {
        List<PortfolioItemRecord> normalized = normalizeItems(items);
        portfolioRepository.saveAll(normalized);
        return new PortfolioResponse(normalized.stream().map(this::toItem).toList());
    }

    /**
     * 向组合中新增一只股票，默认状态为 watch。
     * 仅当该代码尚未存在于组合中时才会保存。
     */
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

    /**
     * 删除组合中的指定股票，用于用户移除持仓或关注记录。
     */
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
