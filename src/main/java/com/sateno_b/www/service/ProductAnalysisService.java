package com.sateno_b.www.service;

import com.sateno_b.www.model.dto.ProductAnalysisItemDto;
import com.sateno_b.www.model.entity.WpOrderEntity;
import com.sateno_b.www.model.entity.data.OrderLineItem;
import com.sateno_b.www.model.repository.WpOrderRepository;
import com.sateno_b.www.model.repository.WpProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProductAnalysisService {

    private final WpOrderRepository wpOrderRepository;
    private final WpProductRepository wpProductRepository;

    @Value("${financial.dashboard.site-ids:6}")
    private List<Long> dashboardSiteIds;

    /**
     * Връща ВСИЧКИ продукти (с ненулев SKU) с броя им поръчки за периода.
     * Продукти без нито една поръчка получават 0 и рейтинг D.
     */
    @Transactional(readOnly = true)
    public List<ProductAnalysisItemDto> analyze(
            LocalDate from, LocalDate to,
            String timeZone,
            int dMax, int cMax, int bMax) {

        ZoneId zone = resolveZone(timeZone);
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();

        // 1. Всички продукти — стартова точка с 0 поръчки
        // row[0]=sku, row[1]=bgName (може null), row[2]=anyName (fallback)
        Map<String, String> namesBySku = new LinkedHashMap<>();
        for (Object[] row : wpProductRepository.findAllSkusWithName()) {
            String sku = (String) row[0];
            String name = row[1] != null ? (String) row[1]
                        : row[2] != null ? (String) row[2]
                        : sku;
            namesBySku.put(sku, name);
        }

        // 2. Брояч — всички SKU-та започват от 0
        Map<String, Integer> countsBySku = new LinkedHashMap<>();
        for (String sku : namesBySku.keySet()) {
            countsBySku.put(sku, 0);
        }

        // 3. Обхождаме ВСИЧКИ поръчки за периода (без филтър по статус)
        List<WpOrderEntity> orders = wpOrderRepository.findAllForPeriod(dashboardSiteIds, start, end);

        for (WpOrderEntity order : orders) {
            if (order.getOrderLine() == null) continue;
            Set<String> seenSkus = new HashSet<>();
            for (OrderLineItem line : order.getOrderLine()) {
                String sku = line.getSku();
                if (sku == null || sku.isBlank()) continue;
                // Ако SKU не е в каталога (напр. изтрит продукт), добавяме го с name от поръчката
                namesBySku.putIfAbsent(sku, line.getProductName() != null ? line.getProductName() : sku);
                countsBySku.putIfAbsent(sku, 0);
                if (seenSkus.add(sku)) {
                    countsBySku.merge(sku, 1, Integer::sum);
                }
            }
        }

        // 4. Сглобяваме резултата
        List<ProductAnalysisItemDto> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : countsBySku.entrySet()) {
            String sku = entry.getKey();
            int count = entry.getValue();
            ProductAnalysisItemDto dto = new ProductAnalysisItemDto();
            dto.setSku(sku);
            dto.setProductName(namesBySku.getOrDefault(sku, sku));
            dto.setOrderCount(count);
            dto.setRating(computeRating(count, dMax, cMax, bMax));
            result.add(dto);
        }

        result.sort(Comparator.comparingInt(ProductAnalysisItemDto::getOrderCount).reversed()
                .thenComparing(ProductAnalysisItemDto::getProductName));
        return result;
    }

    private String computeRating(int count, int dMax, int cMax, int bMax) {
        if (count <= dMax) return "D";
        if (count <= cMax) return "C";
        if (count <= bMax) return "B";
        return "A";
    }

    private ZoneId resolveZone(String timeZone) {
        if (timeZone == null || timeZone.isBlank()) return ZoneId.of("Europe/Sofia");
        try { return ZoneId.of(timeZone); } catch (Exception e) { return ZoneId.of("Europe/Sofia"); }
    }
}
