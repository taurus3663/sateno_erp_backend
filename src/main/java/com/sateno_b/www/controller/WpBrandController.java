package com.sateno_b.www.controller;

import com.sateno_b.www.model.dto.WpBrandDto;
import com.sateno_b.www.model.entity.WpBrandEntity;
import com.sateno_b.www.model.repository.SiteRepository;
import com.sateno_b.www.model.repository.WpBrandRepository;
import com.sateno_b.www.model.repository.WpBrandWpIdRepository;
import com.sateno_b.www.service.WpBrandService;
import com.sateno_b.www.shared.SlugTool;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping("/wp_brand")
public class WpBrandController {

    private final WpBrandWpIdRepository wpBrandWpIdRepository;
    private final WpBrandRepository wpBrandRepository;
    private final ModelMapper modelMapper;
    private final WpBrandService wpBrandService;

    @GetMapping("/list")
    public ResponseEntity<Page<WpBrandDto>> getList(Pageable pageable){
        Page<WpBrandEntity> p =  wpBrandRepository.findAll(pageable);
        Page<WpBrandDto> dto =  p.map(entity -> modelMapper.map(entity, WpBrandDto.class));
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/detail/{id}")
    public ResponseEntity<WpBrandDto> getWpBrandDetail(@PathVariable Long id){
        Optional<WpBrandEntity> byId = wpBrandRepository.findById(id);
        WpBrandDto map = modelMapper.map(byId, WpBrandDto.class);
        return ResponseEntity.ok(map);
    }

    @PostMapping("/save")
    public ResponseEntity<WpBrandDto> saveWpBrand(@RequestBody WpBrandDto wpBrandDto){

        WpBrandDto wpBrandDto1 = wpBrandService.saveBrand(wpBrandDto);
        if(wpBrandDto1 == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(wpBrandDto1);

//        if(wpBrandDto.getId() == null || wpBrandDto.getId()==0){
//            WpBrandEntity wpBrandEntity = modelMapper.map(wpBrandDto, WpBrandEntity.class);
//            wpBrandEntity.setSlug(SlugTool.generateSlug(wpBrandEntity.getName()));
//            WpBrandEntity save = wpBrandRepository.save(wpBrandEntity);
//            return ResponseEntity.ok(modelMapper.map(save, WpBrandDto.class));
//        }
//
//        return wpBrandRepository.findById(wpBrandDto.getId())
//                .map(entity -> {
//                    modelMapper.map(wpBrandDto, entity);
//                    return ResponseEntity.ok(modelMapper.map(wpBrandRepository.save(entity), WpBrandDto.class));
//                }).orElse(ResponseEntity.notFound().build());
    }

//    @PostMapping("/sync/{siteId}")
//    public void syncWpBrand(@PathVariable Long siteId){
//        wpBrandService.syncBrandsToDB(siteId);
//    }

    @DeleteMapping("/delete")
    public ResponseEntity<Void> delete(@RequestBody List<Long> ids) {
        ids.forEach(wpBrandRepository::deleteById);
        return ResponseEntity.ok().build();
    }
}
