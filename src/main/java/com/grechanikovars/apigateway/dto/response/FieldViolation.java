package com.grechanikovars.apigateway.dto.response;

/**
 * One invalid field of a request, as it is listed in the {@code errors} of a 400 answer.
 *
 * <p>It holds the name of the field and the text of the rule, never the rejected value: the value
 * may be a password or an email.
 *
 * @param field the name of the field
 * @param message the text of the violated rule
 */
public record FieldViolation(String field, String message) {
}
