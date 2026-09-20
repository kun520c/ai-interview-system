package com.kun.aiinterview.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = Utf8ByteSizeValidator.class)
@Target({
        ElementType.FIELD,
        ElementType.METHOD,
        ElementType.PARAMETER,
        ElementType.ANNOTATION_TYPE,
        ElementType.TYPE_USE,
        ElementType.RECORD_COMPONENT
})
@Retention(RetentionPolicy.RUNTIME)
public @interface Utf8ByteSize {

    int MYSQL_TEXT_MAX_BYTES = 65_535;

    String message() default "UTF-8字节长度超出限制";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    int max() default MYSQL_TEXT_MAX_BYTES;
}
