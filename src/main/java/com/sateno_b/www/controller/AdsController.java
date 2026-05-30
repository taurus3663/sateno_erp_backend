package com.sateno_b.www.controller;

import com.sateno_b.www.model.dto.MetaAdsDto;
import com.sateno_b.www.model.dto.MetaAdsRecordEntityDto;
import com.sateno_b.www.model.entity.MetaAdsCampaignName;
import com.sateno_b.www.model.entity.MetaAdsEntity;
import com.sateno_b.www.model.entity.MetaAdsRecordEntity;
import com.sateno_b.www.model.repository.MetaAdsCampaignNameRepository;
import com.sateno_b.www.model.repository.MetaAdsRecordRepository;
import com.sateno_b.www.model.repository.MetaAdsRepository;
import com.sateno_b.www.service.MetaAdsService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/ads")
@RequiredArgsConstructor
public class AdsController {

    private final MetaAdsService metaAdsService;
    private final ModelMapper modelMapper;
    private final MetaAdsRepository metaAdsRepository;
    private final MetaAdsCampaignNameRepository metaAdsCampaignNameRepository;
    private final MetaAdsRecordRepository metaAdsRecordRepository;


    @GetMapping("/meta/list")
    public ResponseEntity<Page<MetaAdsDto>> getAll(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());

        Page<MetaAdsDto> list = metaAdsRepository.findAll(pageable)
                .map(s -> modelMapper.map(s, MetaAdsDto.class));

        return ResponseEntity.ok(list);
    }

    @GetMapping("/meta/{id}")
    public ResponseEntity<MetaAdsDto> get(@PathVariable Long id) {
        Optional<MetaAdsEntity> byId = metaAdsRepository.findById(id);
        if(byId.isPresent()) {
            MetaAdsDto dto = modelMapper.map(byId.get(), MetaAdsDto.class);
            return ResponseEntity.ok(dto);
    }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/meta/save")
    public ResponseEntity<MetaAdsDto> save(@RequestBody MetaAdsDto dto) {
        MetaAdsEntity metaAdsEntity;

        if(dto.getId() == null || dto.getId() == 0) {
            metaAdsEntity = modelMapper.map(dto, MetaAdsEntity.class);
        }
        else {

            metaAdsEntity = metaAdsRepository.findById(dto.getId())
                    .orElseThrow(() -> new RuntimeException("Meta ads не е намерен"));

            metaAdsEntity.setAccessToken(dto.getAccessToken());
            metaAdsEntity.setAdAccountId(dto.getAdAccountId());
            metaAdsEntity.setName(dto.getName());
            metaAdsEntity.setActive(dto.isActive());
        }

        MetaAdsEntity saved = metaAdsRepository.save(metaAdsEntity);
        return ResponseEntity.ok(modelMapper.map(saved, MetaAdsDto.class));
    }

    @GetMapping("/meta/campaign/{id}")
    public ResponseEntity<Object> getCampaign(@PathVariable Long id) {
        MetaAdsEntity referenceById = metaAdsRepository.getReferenceById(id);

        List<MetaAdsCampaignName> distinctCampaignsByAd = metaAdsRecordRepository.findDistinctCampaignsByAd(referenceById);


        return ResponseEntity.ok(distinctCampaignsByAd);
    }

    @GetMapping("/meta/campaign/adsrecords")
    public ResponseEntity<Object> getAdsRecords(
            @RequestParam Long id,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        MetaAdsCampaignName campaign = metaAdsCampaignNameRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Campaign not found"));

        LocalDate startLocal = (from != null && !from.isEmpty())
                ? LocalDate.parse(from)
                : LocalDate.now().withDayOfYear(1);

        LocalDate endLocal = (to != null && !to.isEmpty())
                ? LocalDate.parse(to)
                : LocalDate.now();

        // 2. Превръщане в Instant, за да работи със заявката ти
        Instant start = startLocal.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = endLocal.atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC);

        List<MetaAdsRecordEntity> records = metaAdsRecordRepository.findByCampaignAndDateRange(campaign, start, end);

        // Логика за избор на формат:
        // 1. Ако е един ден -> "HH:mm"
        // 2. Ако е един месец или повече -> "yyyy-MM" (за месечно групиране)
        // 3. Иначе -> "yyyy-MM-dd"
        Map<String, List<MetaAdsRecordEntityDto>> grouped = records.stream()
                .collect(Collectors.groupingBy(r -> {
                    LocalDate date = r.getRecordedAt().atZone(ZoneOffset.UTC).toLocalDate();

                    // Проверка за един и същ ден
                    if (start.equals(end) || LocalDate.ofInstant(start, ZoneOffset.UTC).equals(LocalDate.ofInstant(end, ZoneOffset.UTC))) {
                        return r.getRecordedAt().atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("HH:mm"));
                    }

                    // Ако периодът обхваща повече от 31 дни, групирай по месец
                    if (ChronoUnit.DAYS.between(LocalDate.ofInstant(start, ZoneOffset.UTC), LocalDate.ofInstant(end, ZoneOffset.UTC)) > 31) {
                        return date.format(DateTimeFormatter.ofPattern("yyyy-MM"));
                    }

                    return date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                }, TreeMap::new, Collectors.mapping(r -> {
                    MetaAdsRecordEntityDto dto = new MetaAdsRecordEntityDto();
                    dto.setSpend(r.getSpend());
                    dto.setClicks(r.getClicks());
                    dto.setImpressions(r.getImpressions());
                    dto.setCpc(r.getCpc());
                    dto.setCpm(r.getCpm());
                    dto.setCtr(r.getCtr());
                    dto.setRecordedAt(r.getRecordedAt());
                    return dto;
                }, Collectors.toList())));

        System.out.println(grouped.toString());
        return ResponseEntity.ok(grouped);
    }


}
