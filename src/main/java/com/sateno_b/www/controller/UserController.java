package com.sateno_b.www.controller;

import com.sateno_b.www.model.dto.UserDto;
import com.sateno_b.www.model.entity.UserEntity;
import com.sateno_b.www.model.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final ModelMapper modelMapper;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/list")
    public ResponseEntity<Page<UserDto>> getAllUsers(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size
    ) {

        Pageable pageable = PageRequest.of(page, size);
        Page<UserEntity> list = userRepository.findAll(pageable);
        Page<UserDto> dtoPage = list.map(userEntity -> modelMapper.map(userEntity, UserDto.class));

        return ResponseEntity.ok(dtoPage);
    }

    @PostMapping("/save")
    public ResponseEntity<UserDto> saveUser(@RequestBody UserDto userDto) {

        if(userDto.getId()==null || userDto.getId()==0){

            UserEntity userEntity = modelMapper.map(userDto, UserEntity.class);
            userEntity.setPassword(passwordEncoder.encode(userDto.getPassword()));
            UserDto userDto1 = modelMapper.map(userRepository.save(userEntity), UserDto.class);
            return ResponseEntity.ok(userDto1);
        }

        return userRepository.findById(userDto.getId())
                .map(userEntity -> {
                    modelMapper.typeMap(userDto.getClass(), userEntity.getClass()).addMappings(mapper -> {
                        mapper.skip(UserEntity::setPassword);
                    });
                    modelMapper.map(userDto, userEntity);

                    if(userDto.getPassword()!=null){
                        userEntity.setPassword(passwordEncoder.encode(userDto.getPassword()));
                    }
                    UserEntity updated = userRepository.save(userEntity);
                    return ResponseEntity.ok(modelMapper.map(updated, UserDto.class));
                }).orElse(ResponseEntity.notFound().build());

//
    }

}
