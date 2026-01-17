package com.sateno_b.www.controller;

import com.sateno_b.www.model.dto.CustomerDto;
import com.sateno_b.www.model.entity.CustomerEntity;
import com.sateno_b.www.model.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/customer")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerRepository customerRepository;
    private final ModelMapper modelMapper;


    @GetMapping("/list")
    public ResponseEntity<Page<CustomerDto>> getAllCustomers(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<CustomerEntity> list = customerRepository.findAll(pageable);
        Page<CustomerDto> dtoPage = list.map(entity -> modelMapper.map(entity, CustomerDto.class));

        return ResponseEntity.ok(dtoPage);
    }

    @PostMapping("/save")
    public ResponseEntity<CustomerDto> saveCustomer(@RequestBody CustomerDto customerDto) {
       if(customerDto.getId()==null || customerDto.getId()==0){
           CustomerEntity customerEntity = modelMapper.map(customerDto, CustomerEntity.class);
           CustomerEntity savedEntity = customerRepository.save(customerEntity);
           return ResponseEntity.ok(modelMapper.map(savedEntity, CustomerDto.class));
       }

       return customerRepository.findById(customerDto.getId())
               .map(entity -> {
                  modelMapper.map(customerDto, entity);
                   CustomerEntity save = customerRepository.save(entity);
                   return ResponseEntity.ok(modelMapper.map(save, CustomerDto.class));
               }).orElse(ResponseEntity.notFound().build());
    }


}
