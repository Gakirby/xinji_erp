package com.xinji.erp.config;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 全局时间格式。
 *
 * 默认 Jackson 只认 ISO 的 2026-11-03T07:30:00，
 * 但前端日期控件、Excel 导出、运维手工调接口给的都是 2026-11-03 07:30:00。
 * 这里统一成「空格分隔」，同时保留 ISO 兼容（反序列化时两种都能吃下）。
 */
@Configuration
public class JacksonConfig {

    private static final String DATE_TIME = "yyyy-MM-dd HH:mm:ss";
    private static final String DATE = "yyyy-MM-dd";
    private static final String TIME = "HH:mm:ss";

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jsr310Customizer() {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(DATE_TIME);
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(DATE);
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern(TIME);

        return builder -> {
            SimpleModule module = new SimpleModule();
            module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(dateTimeFormatter));
            module.addSerializer(LocalDate.class, new LocalDateSerializer(dateFormatter));
            module.addSerializer(LocalTime.class, new LocalTimeSerializer(timeFormatter));
            module.addDeserializer(LocalDateTime.class,
                    new LenientLocalDateTimeDeserializer(dateTimeFormatter));
            module.addDeserializer(LocalDate.class, new LocalDateDeserializer(dateFormatter));
            module.addDeserializer(LocalTime.class, new LocalTimeDeserializer(timeFormatter));
            builder.modules(module);
        };
    }

    /** 兼容 2026-11-03 07:30:00 与 2026-11-03T07:30:00 两种写法 */
    static class LenientLocalDateTimeDeserializer extends LocalDateTimeDeserializer {

        LenientLocalDateTimeDeserializer(DateTimeFormatter formatter) {
            super(formatter);
        }

        @Override
        public LocalDateTime deserialize(com.fasterxml.jackson.core.JsonParser p,
                                         com.fasterxml.jackson.databind.DeserializationContext ctx)
                throws java.io.IOException {
            String text = p.getText();
            if (text != null && text.indexOf('T') > 0) {
                return LocalDateTime.parse(text);
            }
            return super.deserialize(p, ctx);
        }
    }
}
