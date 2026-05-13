package com.sateno_b.www.model.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Convert;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Converter
public class LongListConverter implements AttributeConverter<List<Long>, String> {
    @Override
    public String convertToDatabaseColumn(List<Long> list) {
        return (list == null || list.isEmpty()) ? "" :
                list.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    @Override
    public List<Long> convertToEntityAttribute(String joined) {
        if (joined == null || joined.isEmpty()) return new ArrayList<>();
        return Arrays.stream(joined.split(","))
                .map(Long::parseLong)
                .collect(Collectors.toList());
    }
}
