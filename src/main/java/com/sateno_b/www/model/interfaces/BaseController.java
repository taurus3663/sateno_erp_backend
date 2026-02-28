package com.sateno_b.www.model.interfaces;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

public interface BaseController<D, ID> {

    @GetMapping("/list")
    Page<D> list(Pageable pageable);
    @GetMapping("/{id}")
    D get(ID id);
    @PostMapping("/save")
    D save(D dto);
    @DeleteMapping("/{id}")
    boolean delete(@PathVariable ID id);
}
