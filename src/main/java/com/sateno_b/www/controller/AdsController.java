package com.sateno_b.www.controller;

import com.sateno_b.www.model.dto.MetaAdsDto;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
            @RequestParam(required = true) Long id,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        MetaAdsCampaignName campaign = metaAdsCampaignNameRepository.findById(id).orElseThrow();
        List<MetaAdsRecordEntity> records = metaAdsRecordRepository.findByCampaignAndDateRange(
                campaign, Instant.parse(from), Instant.parse(to));

        boolean isSameDay = LocalDate.ofInstant(Instant.parse(from), ZoneId.of("UTC"))
                .equals(LocalDate.ofInstant(Instant.parse(to), ZoneId.of("UTC")));

        // Групиране без DTO (използваме Map)
        Map<String, List<MetaAdsRecordEntity>> grouped = records.stream()
                .collect(Collectors.groupingBy(r -> {
                    DateTimeFormatter formatter = isSameDay
                            ? DateTimeFormatter.ofPattern("HH:mm")
                            : DateTimeFormatter.ofPattern("yyyy-MM-dd");
                    return r.getRecordedAt().atZone(ZoneId.of("UTC")).format(formatter);
                }));

        return ResponseEntity.ok(grouped);
    }


}
