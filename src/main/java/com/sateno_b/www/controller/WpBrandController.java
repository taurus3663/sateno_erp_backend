package com.sateno_b.www.controller;

import com.sateno_b.www.model.dto.WpBrandDto;
import com.sateno_b.www.model.entity.WpBrandEntity;
import com.sateno_b.www.model.repository.WpBrandRepository;
import com.sateno_b.www.model.repository.WpBrandWpIdRepository;
import com.sateno_b.www.shared.SlugTool;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/wp_brand")
public class WpBrandController {

    private final WpBrandWpIdRepository wpBrandWpIdRepository;
    private final WpBrandRepository wpBrandRepository;
    private final ModelMapper modelMapper;

    @GetMapping("/list")
    public ResponseEntity<Page<WpBrandDto>> getList(Pageable pageable){
        Page<WpBrandEntity> p =  wpBrandRepository.findAll(pageable);
        Page<WpBrandDto> dto =  p.map(entity -> modelMapper.map(entity, WpBrandDto.class));
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/save")
    public ResponseEntity<WpBrandDto> saveWpBrand(@RequestBody WpBrandDto wpBrandDto){
        if(wpBrandDto.getId() == null || wpBrandDto.getId()==0){
            WpBrandEntity wpBrandEntity = modelMapper.map(wpBrandDto, WpBrandEntity.class);
            wpBrandEntity.setSlug(SlugTool.generateSlug(wpBrandEntity.getName()));
            WpBrandEntity save = wpBrandRepository.save(wpBrandEntity);
            return ResponseEntity.ok(modelMapper.map(save, WpBrandDto.class));
        }

        return wpBrandRepository.findById(wpBrandDto.getId())
                .map(entity -> {
                    modelMapper.map(wpBrandDto, entity);
                    return ResponseEntity.ok(modelMapper.map(wpBrandRepository.save(entity), WpBrandDto.class));
                }).orElse(ResponseEntity.notFound().build());
    }
}
