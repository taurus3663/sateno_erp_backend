package com.sateno_b.www.model.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AIProductGenDTO {
    private Long schemeId;
    private Long step;
    private String refinement;
    private List<WpProductImageDto> tempImages;
    private WpProductDto productInfo;
    private String responseAI;
    private Map<Long, String> previousTexts;
}
