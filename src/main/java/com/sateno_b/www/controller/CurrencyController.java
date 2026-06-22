package com.sateno_b.www.controller;

import com.sateno_b.www.model.dto.CurrencyDto;
import com.sateno_b.www.model.entity.CurrencyEntity;
import com.sateno_b.www.model.repository.CurrencyRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/currency")
@RequiredArgsConstructor
public class CurrencyController {

    private final CurrencyRepository currencyRepository;
    private final ModelMapper modelMapper;

    @GetMapping("/list")
    public ResponseEntity<Page<CurrencyDto>> getAllCurrencies(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<CurrencyEntity> list =  currencyRepository.findAll(pageable);
        Page<CurrencyDto> dtoPage = list.map(entity -> modelMapper.map(entity, CurrencyDto.class));
        return ResponseEntity.ok(dtoPage);
    }

    @GetMapping("/detail/{id}")
    public ResponseEntity<CurrencyDto> getCurrencyById(@PathVariable Long id) {
        Optional<CurrencyEntity> currencyEntity = currencyRepository.findById(id);
        CurrencyDto currencyDto = modelMapper.map(currencyEntity, CurrencyDto.class);
        return ResponseEntity.ok(currencyDto);
    }

    @PostMapping("/save")
    public ResponseEntity<CurrencyDto> saveCurrency(@RequestBody CurrencyDto currencyDto) {
        if(currencyDto.getId() ==null || currencyDto.getId()==0){
            CurrencyEntity currencyEntity = modelMapper.map(currencyDto, CurrencyEntity.class);
            CurrencyEntity savedEntity = currencyRepository.save(currencyEntity);
            return ResponseEntity.ok(modelMapper.map(savedEntity, CurrencyDto.class));
        }

        return currencyRepository.findById(currencyDto.getId())
                .map(entity -> {
                    modelMapper.map(currencyDto, entity);
                    CurrencyEntity save = currencyRepository.save(entity);
                    return ResponseEntity.ok(modelMapper.map(save, CurrencyDto.class));
                }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/delete")
    public ResponseEntity<Void> delete(@RequestBody List<Long> ids) {
        ids.forEach(currencyRepository::deleteById);
        return ResponseEntity.ok().build();
    }
}
