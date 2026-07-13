package com.medibook.api.dto.Auth;

/**
 * Internal service-level result of a sign-in / token-refresh.
 *
 * <p>Carries the JSON {@link SignInResponseDTO} body <b>and</b> the raw refresh token
 * separately so the controller can put the raw token into the httpOnly cookie WITHOUT
 * it ever appearing in the response body. Never serialize this record to a client and
 * never log {@link #refreshToken()}.
 */
public record SignInResultDTO(SignInResponseDTO response, String refreshToken) {}
