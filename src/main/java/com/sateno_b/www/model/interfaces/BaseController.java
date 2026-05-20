package com.sateno_b.www.model.interfaces;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

public interface BaseController<D, ID> {

    @GetMapping("/list")
    default ResponseEntity<Page<D>> list(Pageable pageable, @RequestParam Map<String, String> params) {
        // Ако не го презапишеш в контролера, той автоматично ще вика чистия list(pageable)
        return list(pageable);
    }

    // Втори вариант: чист списък
    default ResponseEntity<Page<D>> list(Pageable pageable) {
        // Тук можеш да хвърлиш грешка или да върнеш empty page по подразбиране
        return ResponseEntity.ok(Page.empty());
    }
    @GetMapping("/{id}")
    ResponseEntity<D> get( @PathVariable ID id);
    @PostMapping("/save")
    ResponseEntity<D> save(@RequestBody D dto);
    @DeleteMapping("/{id}")
    default boolean delete(@PathVariable ID id) {
        // Връща статус 200 OK с тяло false
        return false;
    }
    @DeleteMapping("/delete")
    default boolean deleteMultiple(@RequestBody List<ID> ids) {
        // Преименуван на deleteMultiple, за да няма конфликт при компилация
        return false;
    }
}
