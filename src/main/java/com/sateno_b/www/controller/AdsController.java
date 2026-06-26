package com.sateno_b.www.controller;

import com.sateno_b.www.model.dto.GoogleAdsDto;
import com.sateno_b.www.model.dto.GoogleAdsRecordEntityDto;
import com.sateno_b.www.model.dto.MetaAdsDto;
import com.sateno_b.www.model.dto.MetaAdsRecordEntityDto;
import com.sateno_b.www.model.entity.*;
import com.sateno_b.www.model.repository.*;
import com.sateno_b.www.service.GoogleAdsService;
import com.sateno_b.www.service.MetaAdsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/ads")
@RequiredArgsConstructor
public class AdsController {

    private final MetaAdsService metaAdsService;
    private final ModelMapper modelMapper;
    private final MetaAdsRepository metaAdsRepository;
    private final MetaAdsCampaignNameRepository metaAdsCampaignNameRepository;
    private final MetaAdsRecordRepository metaAdsRecordRepository;
    private final GoogleAdsRepository googleAdsRepository;
    private final GoogleAdsCampaignNameRepository googleAdsCampaignNameRepository;
    private final GoogleAdsRecordRepository googleAdsRecordRepository;
    private final GoogleAdsService googleAdsService;


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
            @RequestParam List<Long> ids,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String timeZone
    ) {
        // Данните се записват с часовата зона на рекламодателя (Europe/Sofia).
        ZoneId storeZone = ZoneId.of("Europe/Sofia");

        List<MetaAdsCampaignName> campaigns;
        if(ids.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyMap());
        } else {
            campaigns = metaAdsCampaignNameRepository.findAllById(ids);
        }

        LocalDate startLocal = (from != null && !from.isEmpty())
                ? LocalDate.parse(from)
                : LocalDate.now().withDayOfYear(1);

        LocalDate endLocal = (to != null && !to.isEmpty())
                ? LocalDate.parse(to)
                : LocalDate.now();

        Instant start = startLocal.atStartOfDay(storeZone).toInstant();
        Instant end = endLocal.atTime(LocalTime.MAX).atZone(storeZone).toInstant();

        List<MetaAdsRecordEntity> records = metaAdsRecordRepository.findByCampaignAndDateRange(campaigns, start, end);

        // Логика за избор на формат:
        // 1. Ако е един ден -> "HH:mm"
        // 2. Ако е един месец или повече -> "yyyy-MM" (за месечно групиране)
        // 3. Иначе -> "yyyy-MM-dd"
        Map<String, List<MetaAdsRecordEntityDto>> grouped = records.stream()
                .collect(Collectors.groupingBy(r -> {
                    ZonedDateTime zonedDateTime = r.getRecordedAt().atZone(storeZone);

                    // 1. Ако началната и крайната дата са еднакви -> HH:mm
                    if (startLocal.equals(endLocal)) {
                        return zonedDateTime.format(DateTimeFormatter.ofPattern("HH:mm"));
                    }

                    // 2. Ако периодът обхваща повече от 31 дни -> yyyy-MM
                    if (ChronoUnit.DAYS.between(startLocal, endLocal) > 31) {
                        return zonedDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM"));
                    }

                    // 3. Иначе -> yyyy-MM-dd
                    return zonedDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
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

        return ResponseEntity.ok(grouped);
    }

    @GetMapping("/google/list")
    public ResponseEntity<Page<GoogleAdsDto>> getAll2(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());

        Page<GoogleAdsDto> list = googleAdsRepository.findAll(pageable)
                .map(s -> modelMapper.map(s, GoogleAdsDto.class));

        return  ResponseEntity.ok(list);
    }

    @GetMapping("/google/{id}")
    public ResponseEntity<GoogleAdsDto> get2(@PathVariable Long id) {
        Optional<GoogleAdsEntity> byId = googleAdsRepository.findById(id);
        if(byId.isPresent()) {
            GoogleAdsDto dto = modelMapper.map(byId.get(), GoogleAdsDto.class);
            return ResponseEntity.ok(dto);
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/google/save")
    public ResponseEntity<GoogleAdsDto> save2(@RequestBody GoogleAdsDto dto) {
        GoogleAdsEntity googleAdsEntity;

        if(dto.getId() == null || dto.getId() == 0) {
//            googleAdsEntity = modelMapper.map(dto, GoogleAdsEntity.class);
            googleAdsEntity = new GoogleAdsEntity();
            googleAdsEntity.setName(dto.getName());
            googleAdsEntity.setActive(dto.isActive());
            googleAdsEntity.setClientId(dto.getClientId());
            googleAdsEntity.setClientSecret(dto.getClientSecret());
            googleAdsEntity.setLoginCustomerId(dto.getLoginCustomerId());
            googleAdsEntity.setDeveloperToken(dto.getDeveloperToken());
//            googleAdsEntity.setRefreshToken(dto.getRefreshToken());
        }
        else {
            googleAdsEntity = googleAdsRepository.findById(dto.getId())
                    .orElseThrow(() -> new RuntimeException("Google ads не е намерен"));
            googleAdsEntity.setActive(dto.isActive());
            googleAdsEntity.setClientId(dto.getClientId());
            googleAdsEntity.setClientSecret(dto.getClientSecret());
            googleAdsEntity.setLoginCustomerId(dto.getLoginCustomerId());
            googleAdsEntity.setName(dto.getName());
            googleAdsEntity.setDeveloperToken(dto.getDeveloperToken());
//            googleAdsEntity.setRefreshToken(dto.getRefreshToken());
        }

        GoogleAdsEntity saved = googleAdsRepository.save(googleAdsEntity);
        return ResponseEntity.ok(modelMapper.map(saved, GoogleAdsDto.class));
    }

    @GetMapping("/google/campaign/{id}")
    public ResponseEntity<Object> getCampaign2(@PathVariable Long id) {
        GoogleAdsEntity referenceById = googleAdsRepository.getReferenceById(id);

        List<GoogleAdsCampaignName> distinctCampaignsByAd = googleAdsRecordRepository.findDistinctCampaignsByAd(referenceById);

        return ResponseEntity.ok(distinctCampaignsByAd);
    }

    @PostMapping("/google/campaign/{id}/token")
    public ResponseEntity<Object> genToken(@PathVariable Long id) {
        try {
            String url = googleAdsService.genUrl(id);
            return ResponseEntity.ok(url); // Връщаме URL-а към Angular
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/google/callback")
    public ResponseEntity<String> callback(
            @RequestParam("code") String code,
            @RequestParam("state") Long id) throws IOException {
        googleAdsService.handleCallback(code, id);
        return ResponseEntity.ok("Токенът е записан за кампания с ID: " + id);
    }

    @GetMapping("/google/campaign/adsrecords")
    public ResponseEntity<Object> getGoogleAdsRecords(
            @RequestParam List<Long> ids,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String timeZone
    ) {
        if (ids.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyMap());
        }

        ZoneId storeZone = (timeZone != null && !timeZone.isEmpty())
                ? ZoneId.of(timeZone)
                : ZoneId.of("Europe/Sofia");

        List<GoogleAdsCampaignName> campaigns = googleAdsCampaignNameRepository.findAllById(ids);

        LocalDate startLocal = (from != null && !from.isEmpty())
                ? LocalDate.parse(from)
                : LocalDate.now().withDayOfYear(1);

        LocalDate endLocal = (to != null && !to.isEmpty())
                ? LocalDate.parse(to)
                : LocalDate.now();

        Instant start = startLocal.atStartOfDay(storeZone).toInstant();
        Instant end = endLocal.atTime(LocalTime.MAX).atZone(storeZone).toInstant();

        List<GoogleAdsRecordEntity> records = googleAdsRecordRepository.findByCampaignAndDateRange(campaigns, start, end);
        Map<String, List<GoogleAdsRecordEntityDto>> grouped = records.stream()
                .collect(Collectors.groupingBy(
                        r -> {
                            ZonedDateTime zdt = r.getRecordedAt().atZone(storeZone);

                            if (startLocal.equals(endLocal)) {
                                return zdt.format(DateTimeFormatter.ofPattern("HH:mm"));
                            }
                            if (ChronoUnit.DAYS.between(startLocal, endLocal) > 31) {
                                return zdt.format(DateTimeFormatter.ofPattern("yyyy-MM"));
                            }
                            return zdt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                        },
                        TreeMap::new,
                        Collectors.mapping(r -> {
                            GoogleAdsRecordEntityDto dto = new GoogleAdsRecordEntityDto();
                            dto.setSpend(r.getSpend());
                            dto.setClicks(r.getClicks());
                            dto.setImpressions(r.getImpressions());
                            dto.setCpc(r.getCpc());
                            dto.setCpm(r.getCpm());
                            dto.setCtr(r.getCtr());
                            dto.setRecordedAt(r.getRecordedAt());
                            return dto;
                        }, Collectors.toList())
                ));

        return ResponseEntity.ok(grouped);
    }

    private String groupingKey(Instant recordedAt, Instant start, Instant end, ZoneId userZone) {
        ZonedDateTime zonedDateTime = recordedAt.atZone(userZone);
        LocalDate startDate = start.atZone(userZone).toLocalDate();
        LocalDate endDate = end.atZone(userZone).toLocalDate();

        if (startDate.equals(endDate)) {
            return zonedDateTime.format(DateTimeFormatter.ofPattern("HH:mm"));
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) > 31) {
            return zonedDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        }
        return zonedDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    @PostMapping("/meta/resync")
    public ResponseEntity<String> resyncMeta() {
        metaAdsService.syncMissingHours();
        return ResponseEntity.ok("Meta resync завършен");
    }

    @PostMapping("/google/resync")
    public ResponseEntity<String> resyncGoogle() {
        googleAdsRecordRepository.deleteAll();
        googleAdsService.syncMissingHours();
        return ResponseEntity.ok("Google resync завършен");
    }

}
