package com.grechanikovars.apigateway.client;

import java.time.LocalDate;

/**
 * The body of {@code POST /internal/users} of the User Service: the profile to create.
 *
 * @param name the first name
 * @param surname the surname
 * @param birthDate the date of birth
 * @param email the email
 */
public record InternalUserRequest(String name, String surname, LocalDate birthDate,
    String email) {
}
