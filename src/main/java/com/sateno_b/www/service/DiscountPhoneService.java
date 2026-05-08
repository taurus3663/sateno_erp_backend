package com.sateno_b.www.service;

import com.sateno_b.www.model.dto.DiscountPhoneDTO;
import com.sateno_b.www.model.entity.DiscountPhone;
import com.sateno_b.www.model.entity.SiteEntity;
import com.sateno_b.www.model.repository.DiscountPhoneRepository;
import com.sateno_b.www.model.repository.SiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Slf4j
@Service
public class DiscountPhoneService {

    private final DiscountPhoneRepository discountPhoneRepository;
    private final SiteRepository siteRepository;

    public boolean saveNewPhone(DiscountPhoneDTO discountPhoneDTO) {

        List<DiscountPhone> byPhoneNumber = discountPhoneRepository.findByPhoneNumber(discountPhoneDTO.getPhone());
        if(!byPhoneNumber.isEmpty()) {
            return false;
        }

        SiteEntity siteEntityByUrl = siteRepository.findSiteEntityByUrl(discountPhoneDTO.getSite());

        DiscountPhone discountPhone = new DiscountPhone();
        discountPhone.setPhoneNumber(discountPhoneDTO.getPhone());
        discountPhone.setSite(siteEntityByUrl);

        discountPhoneRepository.save(discountPhone);
        return true;
    }

    public Page<DiscountPhoneDTO> list(Pageable pageable) {

        return discountPhoneRepository.findAll(pageable)
                .map(discountPhone -> {
                    DiscountPhoneDTO discountPhoneDTO = new DiscountPhoneDTO();
                    discountPhoneDTO.setPhone(discountPhone.getPhoneNumber());
                    discountPhoneDTO.setSite(discountPhone.getSite().getUrl());
                    discountPhoneDTO.setId(discountPhone.getId());
                    discountPhoneDTO.setCreated(discountPhone.getCreateTime().toString());
                    return discountPhoneDTO;
                });
    }
}
