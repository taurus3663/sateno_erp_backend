package com.sateno_b.www.controller;

import com.sateno_b.www.model.dto.LanguageDto;
import com.sateno_b.www.model.entity.LanguageEntity;
import com.sateno_b.www.model.repository.LanguageRepository;
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
@RequestMapping("/language")
@RequiredArgsConstructor
public class LanguageController {

    private final LanguageRepository languageRepository;
    private final ModelMapper modelMapper;

    @GetMapping("/list")
    public ResponseEntity<Page<LanguageDto>> getAllSites(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<LanguageEntity> list = languageRepository.findAll(pageable);
        Page<LanguageDto> dtoPage = list.map(entity -> modelMapper.map(entity, LanguageDto.class));
        return ResponseEntity.ok(dtoPage);
    }

    @GetMapping("/detail/{id}")
    public ResponseEntity<LanguageDto> getLanguage(@PathVariable Long id) {
        Optional<LanguageEntity> languageEntity = languageRepository.findById(id);
        LanguageDto dto = modelMapper.map(languageEntity, LanguageDto.class);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/save")
    public ResponseEntity<LanguageDto> save(@RequestBody LanguageDto dto) {
        LanguageEntity languageEntity;
        if(dto.getId() == null || dto.getId()==0){
            languageEntity = modelMapper.map(dto, LanguageEntity.class);
            LanguageEntity saved = languageRepository.save(languageEntity);
            return ResponseEntity.ok(modelMapper.map(saved, LanguageDto.class));
        }
            return languageRepository.findById(dto.getId())
                    .map(entity -> {
                        modelMapper.map(dto, entity);
                        LanguageEntity savedEntity = languageRepository.save(entity);
                        return ResponseEntity.ok(modelMapper.map(savedEntity, LanguageDto.class));
                    }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/delete")
    public ResponseEntity<Void> delete(@RequestBody List<Long> ids) {
        ids.forEach(languageRepository::deleteById);
        return ResponseEntity.ok().build();
    }
}
