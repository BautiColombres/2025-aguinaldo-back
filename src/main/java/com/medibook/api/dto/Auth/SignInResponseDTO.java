package com.medibook.api.dto.Auth;

import java.util.UUID;

public record SignInResponseDTO(
    UUID id,
    String email,
    String name,
    String surname,
    String role,
    String status,
    String accessToken
) {}
