package com.sateno_b.www.service;

import com.sateno_b.www.model.dto.SchemeWpProductDto;
import com.sateno_b.www.model.entity.SchemeWpProductEntity;
import com.sateno_b.www.model.repository.SchemeWpProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class SchemeWpProductService {

    private  final SchemeWpProductRepository schemeWpProductRepository;
    private final ModelMapper modelMapper;

    public Page<SchemeWpProductDto> getAll(Pageable pageable) {

        Page<SchemeWpProductEntity> rs = schemeWpProductRepository.findAll(pageable);

        Page<SchemeWpProductDto> dtoRs = rs.map(entity ->{
           SchemeWpProductDto sc = new  SchemeWpProductDto();
            sc.setName(entity.getName());
            sc.setDescription(entity.getDescription());
            return sc;
        });
        return dtoRs;
    }

    public SchemeWpProductDto save(SchemeWpProductDto dto){
        SchemeWpProductEntity sp = new SchemeWpProductEntity();
        sp.setName(dto.getName());
        sp.setDescription(dto.getDescription());
        SchemeWpProductEntity save = schemeWpProductRepository.save(sp);
        return  modelMapper.map(save, SchemeWpProductDto.class);
    }
}
