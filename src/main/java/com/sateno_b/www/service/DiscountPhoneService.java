package com.sateno_b.www.service;

import com.sateno_b.www.model.dto.DiscountPhoneDTO;
import com.sateno_b.www.model.entity.CustomerEntity;
import com.sateno_b.www.model.entity.DiscountPhone;
import com.sateno_b.www.model.entity.SiteEntity;
import com.sateno_b.www.model.repository.CustomerRepository;
import com.sateno_b.www.model.repository.DiscountPhoneRepository;
import com.sateno_b.www.model.repository.SiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Slf4j
@Service
public class DiscountPhoneService {

    private final DiscountPhoneRepository discountPhoneRepository;
    private final SiteRepository siteRepository;
    private final CustomerRepository customerRepository;

    public boolean saveNewPhone(DiscountPhoneDTO discountPhoneDTO) {


        List<DiscountPhone> byPhoneNumber = discountPhoneRepository.findByPhoneNumber(discountPhoneDTO.getPhone());
        if(!byPhoneNumber.isEmpty()) {
            return false;
        }

        SiteEntity siteEntityByUrl = siteRepository.findSiteEntityByUrl(discountPhoneDTO.getSite());

        DiscountPhone discountPhone = new DiscountPhone();
        discountPhone.setPhoneNumber(discountPhoneDTO.getPhone());
        discountPhone.setSite(siteEntityByUrl);

        Optional<CustomerEntity> byPhone = customerRepository.findByPhone(discountPhoneDTO.getPhone());
        byPhone.ifPresent(discountPhone::setCustomer);

        discountPhoneRepository.save(discountPhone);
        return true;
    }

    public void saveNewPhoneByOrder(SiteEntity site, CustomerEntity customer) {
        List<DiscountPhone> byPhone = discountPhoneRepository.findByPhoneNumber(customer.getPhone());
        if (byPhone.isEmpty()) {
            DiscountPhone discountPhone = new DiscountPhone();
            discountPhone.setPhoneNumber(customer.getPhone());
            discountPhone.setSite(site);
            discountPhone.setCustomer(customer);
            discountPhoneRepository.save(discountPhone);
        } else {
            DiscountPhone discountPhone = byPhone.get(0);
            discountPhone.setCustomer(customer);
            discountPhoneRepository.save(discountPhone);
        }
    }

    public Page<DiscountPhoneDTO> list(Pageable pageable) {
        // Създаваме нов PageRequest, базиран на текущия, но с принудително сортиране по createTime в DESC ред
        Pageable sortedByNewest = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by("createTime").descending() // Замени "createTime" с точното име на полето в твоя DiscountPhoneEntity
        );

        return discountPhoneRepository.findAll(sortedByNewest)
                .map(discountPhone -> {
                    DiscountPhoneDTO discountPhoneDTO = new DiscountPhoneDTO();
                    discountPhoneDTO.setPhone(discountPhone.getPhoneNumber());
                    discountPhoneDTO.setSite(discountPhone.getSite().getUrl());
                    discountPhoneDTO.setId(discountPhone.getId());
                    discountPhoneDTO.setCreated(discountPhone.getCreateTime().toString());

//                    Optional<CustomerEntity> byPhone = customerRepository.findByPhone(discountPhone.getPhoneNumber());
                    discountPhoneDTO.setHasOrder(discountPhone.getCustomer() != null);
                    return discountPhoneDTO;
                });
    }

    public boolean deleteById(Long id) {
        discountPhoneRepository.deleteById(id);
        return true;
    }
}
