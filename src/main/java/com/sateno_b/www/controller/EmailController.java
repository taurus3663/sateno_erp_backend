package com.sateno_b.www.controller;

import com.sateno_b.www.model.dto.EmailDto;
import com.sateno_b.www.model.entity.EmailEntity;
import com.sateno_b.www.model.interfaces.BaseController;
import com.sateno_b.www.model.repository.EmailRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/email")
@RequiredArgsConstructor
public class EmailController implements BaseController<EmailDto, Long> {

    private final EmailRepository emailRepository;
    private final ModelMapper modelMapper;


    @Override
//    @GetMapping("/list")
    public Page<EmailDto> list(Pageable pageable) {

        return emailRepository.findAll(pageable).map(entity -> {

            EmailDto map = modelMapper.map(entity, EmailDto.class);
            return map;
        });

    }

    @Override
//    @PostMapping("/save")
    public EmailDto save(@RequestBody EmailDto emailDto) {
        EmailEntity map = modelMapper.map(emailDto, EmailEntity.class);
        return modelMapper.map(emailRepository.save(map), EmailDto.class);
    }

    @Override
//    @GetMapping("/{id}")
    public EmailDto get(@PathVariable("id") Long id) {
        Optional<EmailEntity> byId = emailRepository.findById(id);
        return modelMapper.map(byId.get(), EmailDto.class);
    }

    @Override
//    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable Long id) {
        if (emailRepository.existsById(id)) {
            try {
                emailRepository.deleteById(id);
                return true;
            } catch (Exception e) {
                // Може да гръмне заради Foreign Key констрейнт (напр. имейлът е вързан към поръчка)
                return false;
            }
        }
        return false;
    }
}
