//package com.xd.util;
//
//
//import com.fasterxml.jackson.core.JsonProcessingException;
//import com.fasterxml.jackson.core.type.TypeReference;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.fasterxml.jackson.databind.SerializationFeature;
//import com.fasterxml.jackson.databind.DeserializationFeature;
//import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
//import lombok.extern.slf4j.Slf4j;
//
//@Slf4j
//public class JsonUtils {
//
//    // 静态 ObjectMapper，配置通用属性（线程安全）
//    private static final ObjectMapper MAPPER = new ObjectMapper()
//            .registerModule(new JavaTimeModule())          // 支持 LocalDateTime
//            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
//            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES); // 忽略未知字段
//
//    /**
//     * 对象转 JSON 字符串（序列化）
//     */
//    public static String toJson(Object obj) {
//        if (obj == null) return null;
//        try {
//            return MAPPER.writeValueAsString(obj);
//        } catch (JsonProcessingException e) {
//            log.error("对象转 JSON 失败: {}", e.getMessage());
//            return "{}";
//        }
//    }
//
//    /**
//     * JSON 字符串转普通对象（反序列化）
//     */
//    public static <T> T toObject(String json, Class<T> clazz) {
//        if (json == null || json.isEmpty()) return null;
//        try {
//            return MAPPER.readValue(json, clazz);
//        } catch (JsonProcessingException e) {
//            log.error("JSON 转对象失败, json: {}, error: {}", json, e.getMessage());
//            return null;
//        }
//    }
//
//    /**
//     * JSON 字符串转泛型集合/复杂对象
//     * 适用于：List<CodeSmell>, Map<String, Object>, List<CodeIssue>
//     *
//     * List<CodeSmell> list = JsonUtils.toObject(jsonStr, new TypeReference<List<CodeSmell>>(){});
//     */
//    public static <T> T toObject(String json, TypeReference<T> typeRef) {
//        if (json == null || json.isEmpty()) return null;
//        try {
//            return MAPPER.readValue(json, typeRef);
//        } catch (JsonProcessingException e) {
//            log.error("JSON 转复杂对象失败, json: {}, error: {}", json, e.getMessage());
//            return null;
//        }
//    }
//
//}