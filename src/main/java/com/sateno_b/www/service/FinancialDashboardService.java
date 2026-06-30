package com.sateno_b.www.service;

import com.sateno_b.www.model.dto.FinancialCardDto;
import com.sateno_b.www.model.dto.FinancialDashboardDto;
import com.sateno_b.www.model.dto.FinancialMetricsDto;
import com.sateno_b.www.model.entity.CourierSettingsEntity;
import com.sateno_b.www.model.entity.WpOrderEntity;
import com.sateno_b.www.model.entity.WpProductEntity;
import com.sateno_b.www.model.entity.data.OrderLineItem;
import com.sateno_b.www.model.entity.data.OrderSavedCourierSettings;
import com.sateno_b.www.shared.Shared;
import com.sateno_b.www.model.enums.CourierShipmentType;
import com.sateno_b.www.model.enums.OrderStatus;
import com.sateno_b.www.model.repository.CourierSettingsRepository;
import com.sateno_b.www.model.repository.GoogleAdsRecordRepository;
import com.sateno_b.www.model.repository.MetaAdsRecordRepository;
import com.sateno_b.www.model.repository.SiteRepository;
import com.sateno_b.www.model.repository.WpOrderRepository;
import com.sateno_b.www.model.repository.WpProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Сервиз за финансовия дашборд.
 *
 * Всички показатели се изчисляват динамично от базата данни в реално време.
 * Стойностите са в EUR (дашбордът засега е само за България, където цените са в евро).
 *
 * Разширяемост:
 *  - Нов KPI: добави поле в {@link FinancialMetricsDto} и попълни го в {@link #computeMetrics}.
 *  - Нов период (текущ месец, произволен период...): извикай {@link #buildCard} с нови граници
 *    и сложи резултата в {@code extraCards}.
 *  - Бъдещи филтри (държава, магазин, източник, рекламен канал): могат да се подадат
 *    в {@link #computeMetrics} без промяна на основната логика.
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class FinancialDashboardService {

    private final WpOrderRepository wpOrderRepository;
    private final WpProductRepository wpProductRepository;
    private final CourierSettingsRepository courierSettingsRepository;
    private final MetaAdsRecordRepository metaAdsRecordRepository;
    private final GoogleAdsRecordRepository googleAdsRecordRepository;
    private final SiteRepository siteRepository;

    /** Часова зона по подразбиране (България). */
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Sofia");

    /** Магазини, които влизат в дашборда (по подразбиране само sateno.bg нов = id 6). */
    @Value("${financial.dashboard.site-ids:6}")
    private List<Long> dashboardSiteIds;

    /**
     * Статуси, които се броят за реален оборот/продажба.
     * В началото броим Потвърдени + Изпратени + Завършени.
     * Понеже изчислението е динамично, ако по-късно поръчка се върне / откаже след преглед
     * (статусът ѝ се смени), тя автоматично излиза от изчислението — дори след седмици.
     */
    public static final List<OrderStatus> REVENUE_STATUSES = List.of(
            OrderStatus.PROCESSING,
            OrderStatus.APPROVED,
            OrderStatus.WAITING,
            OrderStatus.SENT,
            OrderStatus.COMPLETED,
            OrderStatus.JOINT
    );

    private List<Long> resolveSiteIds(List<Long> siteIds) {
        if (siteIds != null && !siteIds.isEmpty()) return siteIds;
        return siteRepository.findAll().stream().map(s -> s.getId()).toList();
    }

    @Transactional(readOnly = true)
    public FinancialDashboardDto getDashboard(String timeZone, List<Long> siteIds) {
        List<Long> sites = resolveSiteIds(siteIds);
        ZoneId zone = resolveZone(timeZone);
        Instant now = Instant.now();

        LocalDate today = LocalDate.now(zone);
        Instant todayStart = today.atStartOfDay(zone).toInstant();
        Instant yesterdayStart = today.minusDays(1).atStartOfDay(zone).toInstant();

        // Последната ПЪЛНА седмица понеделник–неделя
        LocalDate thisMonday = today.with(java.time.DayOfWeek.MONDAY);
        LocalDate lastMonday = thisMonday.minusWeeks(1);
        Instant weekStart = lastMonday.atStartOfDay(zone).toInstant();              // мин. понеделник 00:00
        Instant weekEnd = thisMonday.atStartOfDay(zone).toInstant();                // тази понеделник 00:00 (изключен)
        Instant prevWeekStart = lastMonday.minusWeeks(1).atStartOfDay(zone).toInstant();

        // 1. ДНЕС: 00:00 → сега. Предходен период: вчера в същия часови прозорец.
        FinancialCardDto todayCard = buildCard(
                "TODAY",
                todayStart, now,
                todayStart.minus(Duration.ofDays(1)), now.minus(Duration.ofDays(1)),
                sites
        );

        // 2. ВЧЕРА: целият предходен ден. Предходен период: денят преди вчера.
        FinancialCardDto yesterdayCard = buildCard(
                "YESTERDAY",
                yesterdayStart, todayStart,
                yesterdayStart.minus(Duration.ofDays(1)), todayStart.minus(Duration.ofDays(1)),
                sites
        );

        // 2б. ОНЯ ДЕН (денят преди вчера). Предходен период: денят преди него.
        Instant dayBeforeStart = today.minusDays(2).atStartOfDay(zone).toInstant();
        FinancialCardDto dayBeforeCard = buildCard(
                "DAY_BEFORE",
                dayBeforeStart, yesterdayStart,
                dayBeforeStart.minus(Duration.ofDays(1)), yesterdayStart.minus(Duration.ofDays(1)),
                sites
        );

        // 3. ПОСЛЕДНАТА ПЪЛНА СЕДМИЦА (понеделник–неделя). Предходен период: седмицата преди нея.
        FinancialCardDto last7Card = buildCard(
                "LAST_7_DAYS",
                weekStart, weekEnd,
                prevWeekStart, weekStart,
                sites
        );

        // 4. МИНАЛ МЕСЕЦ (1-во до последния ден). Предходен период: месецът преди него.
        LocalDate firstOfThisMonth = today.withDayOfMonth(1);
        LocalDate firstOfPrevMonth = firstOfThisMonth.minusMonths(1);
        Instant monthStart = firstOfPrevMonth.atStartOfDay(zone).toInstant();
        Instant monthEnd = firstOfThisMonth.atStartOfDay(zone).toInstant();
        Instant prevMonthStart = firstOfPrevMonth.minusMonths(1).atStartOfDay(zone).toInstant();
        FinancialCardDto lastMonthCard = buildCard(
                "LAST_MONTH",
                monthStart, monthEnd,
                prevMonthStart, monthStart,
                sites
        );

        FinancialDashboardDto dto = new FinancialDashboardDto();
        dto.setToday(todayCard);
        dto.setYesterday(yesterdayCard);
        dto.setDayBeforeYesterday(dayBeforeCard);
        dto.setLast7Days(last7Card);
        dto.setLastMonth(lastMonthCard);
        dto.setCurrency("EUR");
        dto.setGeneratedAt(now);
        return dto;
    }

    /**
     * Карта за ПРОИЗВОЛЕН период [from, to] (включително крайната дата),
     * сравнена с предходния равен по дължина период.
     */
    @Transactional(readOnly = true)
    public FinancialCardDto getPeriodCard(LocalDate from, LocalDate to, String timeZone, List<Long> siteIds) {
        List<Long> sites = resolveSiteIds(siteIds);
        ZoneId zone = resolveZone(timeZone);
        if (to.isBefore(from)) {
            LocalDate tmp = from;
            from = to;
            to = tmp;
        }
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();
        Duration len = Duration.between(start, end);
        return buildCard("CUSTOM", start, end, start.minus(len), start, sites);
    }

    /** Построява една карта (текущ + предходен период). */
    public FinancialCardDto buildCard(String period,
                                      Instant from, Instant to,
                                      Instant prevFrom, Instant prevTo,
                                      List<Long> siteIds) {
        FinancialCardDto card = new FinancialCardDto();
        card.setPeriod(period);
        card.setFrom(from);
        card.setTo(to);
        card.setCurrent(computeMetrics(from, to, siteIds));
        card.setPrevious(computeMetrics(prevFrom, prevTo, siteIds));
        return card;
    }

    /**
     * Изчислява всички показатели за период [from, to).
     */
    public FinancialMetricsDto computeMetrics(Instant from, Instant to, List<Long> siteIds) {
        FinancialMetricsDto m = new FinancialMetricsDto();

        List<WpOrderEntity> orders =
                wpOrderRepository.findForFinancialPeriod(REVENUE_STATUSES, siteIds, from, to);

        // --- Карта SKU -> себестойност за единица (покупна цена + транспортен разход) ---
        Map<String, Double> unitCostBySku = buildUnitCostMap(orders);

        // --- Куриерски настройки (за разхода за доставка за сметка на фирмата) ---
        Map<Long, CourierSettingsEntity> couriersById = courierSettingsRepository.findAll().stream()
                .collect(Collectors.toMap(CourierSettingsEntity::getId, c -> c, (a, b) -> a));

        double revenue = 0d;
        double cogs = 0d;
        double shippingCost = 0d;
        long productsSold = 0L;

        for (WpOrderEntity order : orders) {
            // Ако totalPrice == 0 (карта-платена поръчка редактирана преди fix-а),
            // преизчисляваме от редовете за да не влиза с 0 в приходите.
            BigDecimal orderTotalPrice = order.getTotalPrice();
            if (orderTotalPrice != null && orderTotalPrice.compareTo(BigDecimal.ZERO) > 0) {
                revenue += orderTotalPrice.doubleValue();
            } else if (order.getOrderLine() != null) {
                for (OrderLineItem line : order.getOrderLine()) {
                    revenue += Shared.computeEffectiveLineTotal(line).doubleValue();
                }
            }

            if (order.getOrderLine() != null) {
                for (OrderLineItem line : order.getOrderLine()) {
                    int qty = line.getQuantity();
                    productsSold += qty;
                    Double unitCost = line.getSku() != null ? unitCostBySku.get(line.getSku()) : null;
                    if (unitCost != null) {
                        cogs += unitCost * qty;
                    }
                    // Ако продукт няма въведена покупна цена/транспорт, участва със стойност 0 (пропуска се).
                }
            }

            shippingCost += companyShippingCost(order, couriersById);
        }

        double adSpend = metaAdsRecordRepository.sumSpendBetween(from, to)
                + googleAdsRecordRepository.sumSpendBetween(from, to);

        double grossProfit = revenue - cogs;
        double netProfit = revenue - cogs - shippingCost - adSpend;
        Double roas = adSpend > 0 ? round(revenue / adSpend) : null;
        double margin = revenue > 0 ? round(netProfit / revenue * 100d) : 0d;
        double avgOrderValue = !orders.isEmpty() ? round(revenue / orders.size()) : 0d;
        double avgProductValue = productsSold > 0 ? round(revenue / productsSold) : 0d;
        Double cpa = !orders.isEmpty() ? round(adSpend / orders.size()) : null;
        Double cpis = productsSold > 0 ? round(adSpend / productsSold) : null;

        m.setOrders(orders.size());
        m.setProductsSold(productsSold);
        m.setRevenue(round(revenue));
        m.setAvgOrderValue(avgOrderValue);
        m.setAvgProductValue(avgProductValue);
        m.setCpa(cpa);
        m.setCpis(cpis);
        m.setCogs(round(cogs));
        m.setShippingCost(round(shippingCost));
        m.setAdSpend(round(adSpend));
        m.setGrossProfit(round(grossProfit));
        m.setNetProfit(round(netProfit));
        m.setRoas(roas);
        m.setMargin(margin);
        return m;
    }

    /**
     * Строи карта SKU -> (покупна цена + транспортен разход) за единица.
     * Зарежда всички нужни продукти с една заявка (без N+1).
     */
    private Map<String, Double> buildUnitCostMap(List<WpOrderEntity> orders) {
        Set<String> skus = orders.stream()
                .filter(o -> o.getOrderLine() != null)
                .flatMap(o -> o.getOrderLine().stream())
                .map(OrderLineItem::getSku)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toSet());

        if (skus.isEmpty()) {
            return Map.of();
        }

        Map<String, Double> map = new HashMap<>();
        for (WpProductEntity p : wpProductRepository.findAllBySkuIn(List.copyOf(skus))) {
            if (p.getSku() == null) continue;
            double buy = p.getBuyPrice() != null ? p.getBuyPrice().doubleValue() : 0d;
            double transport = p.getTransportPrice() != null ? p.getTransportPrice().doubleValue() : 0d;
            map.put(p.getSku(), buy + transport);
        }
        return map;
    }

    /**
     * Разход за доставка за сметка на ФИРМАТА за дадена поръчка.
     *
     * Логика (по съществуващата в checkCustomShippingField):
     *  - Ако клиентът е платил доставка (customShippingTotal > 0) -> за сметка на клиента -> 0 разход за фирмата.
     *  - Ако клиентът НЕ е платил (customShippingTotal == 0, напр. безплатна доставка) -> за сметка на фирмата;
     *    разходът = фиксираната цена, която куриерът таксува, според типа пратка (офис/адрес/автомат).
     *
     * Методът е изолиран нарочно — ако логиката се промени, се пипа само тук.
     */
    private double companyShippingCost(WpOrderEntity order, Map<Long, CourierSettingsEntity> couriersById) {
        double customerPaid = order.getCustomShippingTotal(); // getter връща 0, ако е null
        if (customerPaid > 0) {
            return 0d; // клиентът плаща доставката
        }
        if (order.getCourierId() == null) {
            return 0d;
        }
        CourierSettingsEntity courier = couriersById.get(order.getCourierId());
        if (courier == null) {
            return 0d;
        }

        CourierShipmentType type = null;
        OrderSavedCourierSettings saved = order.getSavedCourierBilling();
        if (saved != null) {
            type = saved.getCourierShipmentType();
        }

        Double price = pickFixedPrice(courier, type);
        return price != null ? price : 0d;
    }

    /** Избира фиксираната куриерска цена според типа пратка, с резерва общата фиксирана цена. */
    private Double pickFixedPrice(CourierSettingsEntity c, CourierShipmentType type) {
        Double price = null;
        if (type != null) {
            switch (type) {
                case OFFICE -> price = c.getOfficeFixedShippingPrice();
                case ADDRESS -> price = c.getAddressFixedShippingPrice();
                case LOCKER -> price = c.getLockerFixedShippingPrice();
            }
        }
        if (price == null) {
            price = c.getFixedShippingPrice();
        }
        return price;
    }

    private ZoneId resolveZone(String timeZone) {
        if (timeZone == null || timeZone.isBlank()) {
            return DEFAULT_ZONE;
        }
        try {
            return ZoneId.of(timeZone);
        } catch (Exception e) {
            log.warn("Невалидна часова зона '{}', използвам {}", timeZone, DEFAULT_ZONE);
            return DEFAULT_ZONE;
        }
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
