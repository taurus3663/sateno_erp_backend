package com.sateno_b.www.controller;

import com.sateno_b.www.model.dto.CheckCourierRequest;
import com.sateno_b.www.model.dto.CheckOutCourierDto;
import com.sateno_b.www.model.dto.CheckOutCourierListDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/checkout")
public class CheckOutController {



    @PostMapping("/check-couriers")
    public ResponseEntity<CheckOutCourierListDto> check(@RequestBody CheckCourierRequest request) {

        System.out.println(request.toString());

        CheckOutCourierListDto checkCourierDto = new CheckOutCourierListDto();
        return new ResponseEntity<>(checkCourierDto, HttpStatus.OK);
    }

}
