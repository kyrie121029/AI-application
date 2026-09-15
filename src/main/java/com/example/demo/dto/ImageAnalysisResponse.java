package com.example.demo.dto;

import com.example.demo.model.ImageAnalysis;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * 图片分析结果响应。
 */
public class ImageAnalysisResponse {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private String summary;
    private List<String> objects;
    private String scene;
    private String textContent;
    private List<String> riskFlags;
    private String modelName;

    private ImageAnalysisResponse() {}

    public static ImageAnalysisResponse from(ImageAnalysis a) {
        ImageAnalysisResponse r = new ImageAnalysisResponse();
        r.summary = a.getSummary();
        r.objects = readList(a.getObjectsJson());
        r.scene = a.getScene();
        r.textContent = a.getTextContent();
        r.riskFlags = readList(a.getRiskFlagsJson());
        r.modelName = a.getModelName();
        return r;
    }

    private static List<String> readList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return MAPPER.readValue(json, STRING_LIST);
        } catch (Exception e) {
            return List.of();
        }
    }

    public String getSummary() { return summary; }
    public List<String> getObjects() { return objects; }
    public String getScene() { return scene; }
    public String getTextContent() { return textContent; }
    public List<String> getRiskFlags() { return riskFlags; }
    public String getModelName() { return modelName; }
}
