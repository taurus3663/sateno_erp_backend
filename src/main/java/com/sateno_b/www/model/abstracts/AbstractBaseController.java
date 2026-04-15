//package com.sateno_b.www.model.abstracts;
//
//import com.sateno_b.www.model.interfaces.BaseController;
//import lombok.RequiredArgsConstructor;
//import org.modelmapper.ModelMapper;
//import org.springframework.data.domain.Page;
//import org.springframework.data.domain.Pageable;
//import org.springframework.data.jpa.repository.JpaRepository;
//import org.springframework.web.bind.annotation.DeleteMapping;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.PostMapping;
//
//@RequiredArgsConstructor
//public class AbstractBaseController<E, D, ID> implements BaseController<D, ID> {
//
//    protected final JpaRepository<E, ID> repository;
//    protected final ModelMapper modelMapper;
//    private final Class<E> entityClass;
//    private final Class<D> dtoClass;
//
//    @Override
//    @GetMapping("/list")
//    public Page<D> list(Pageable pageable) {
//        return repository.findAll(pageable)
//                .map(entity -> modelMapper.map(entity, dtoClass));
//    }
//
//    @Override
//    @GetMapping("/{id}")
//    public D get(ID id) {
//        E entity = repository.findById(id)
//                .orElseThrow(() -> new RuntimeException("Entity not found with id: " + id));
//        return modelMapper.map(entity, dtoClass);
//    }
//
//    @Override
//    @PostMapping("/save")
//    public D save(D dto) {
//        E entity = modelMapper.map(dto, entityClass);
//        E savedEntity = repository.save(entity);
//        return modelMapper.map(savedEntity, dtoClass);
//    }
//
//    @Override
//    @DeleteMapping("/{id}")
//    public boolean delete(ID id) {
//        if (repository.existsById(id)) {
//            try {
//                repository.deleteById(id);
//                return true;
//            } catch (Exception e) {
//                // Може да гръмне заради Foreign Key констрейнт (напр. имейлът е вързан към поръчка)
//                return false;
//            }
//        }
//        return false;
//    }
//}
