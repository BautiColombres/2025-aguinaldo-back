package com.medibook.api.controller;

import java.util.Arrays;

import com.medibook.api.dto.Rating.RatingRequestDTO;
import com.medibook.api.dto.Rating.RatingResponseDTO;
import com.medibook.api.entity.Rating;
import com.medibook.api.entity.User;
import com.medibook.api.service.TurnAssignedService;
import com.medibook.api.util.ErrorResponseUtil;
import com.medibook.api.mapper.RatingMapper;
import com.medibook.api.repository.RatingRepository;
import com.medibook.api.dto.Rating.SubcategoryCountDTO;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ratings")
@RequiredArgsConstructor
@Slf4j
public class RatingController {

    private final TurnAssignedService turnService;
    private final RatingMapper ratingMapper;
    private final RatingRepository ratingRepository;

    @PostMapping("/turns/{turnId}/rate")
    public ResponseEntity<Object> rateTurn(
            @PathVariable java.util.UUID turnId,
            @Valid @RequestBody RatingRequestDTO dto,
            Authentication authentication,
            HttpServletRequest request) {

        User authenticatedUser = com.medibook.api.util.AuthorizationUtil.extractAuthenticatedUser(authentication);
        if (authenticatedUser == null) {
            return com.medibook.api.util.AuthorizationUtil.createUnauthenticatedResponse();
        }

        if (!"PATIENT".equals(authenticatedUser.getRole()) && !"DOCTOR".equals(authenticatedUser.getRole())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Only patients and doctors can rate");
        }

        try {
            Rating saved = turnService.addRating(turnId, authenticatedUser.getId(), dto.getScore(), dto.getSubcategories());

            RatingResponseDTO ratingDto = ratingMapper.toDTO(saved);
            if (!"ADMIN".equals(authenticatedUser.getRole())) {
                ratingDto.setRaterId(null);
            }
            return ResponseEntity.ok(ratingDto);
        } catch (RuntimeException e) {
            var resp = ErrorResponseUtil.createBadRequestResponse(e.getMessage(), request.getRequestURI());
            return new ResponseEntity<Object>(resp.getBody(), resp.getStatusCode());
        }
    }

    @GetMapping("/rated/{ratedId}/subcategories-counts")
    public ResponseEntity<Object> getSubcategoryCounts(
            @PathVariable java.util.UUID ratedId,
            @RequestParam(required = false) String raterRole) {

        try {
            java.util.List<RatingRepository.SubcategoryCount> results = ratingRepository.countSubcategoriesByRatedId(ratedId, raterRole);

            java.util.List<SubcategoryCountDTO> dto = results.stream()
                    .map(r -> new SubcategoryCountDTO(r.getSubcategory(), r.getCount()))
                    .toList();

            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            var resp = ErrorResponseUtil.createBadRequestResponse(e.getMessage(), "/api/ratings/rated/" + ratedId + "/subcategories-counts");
            return new ResponseEntity<Object>(resp.getBody(), resp.getStatusCode());
        }
    }

    @GetMapping("/rating-subcategories")
    public ResponseEntity<Object> getRatingSubcategories(@RequestParam(required = false) String role) {

        // When DOCTOR is rating a PATIENT, show patient subcategories
        if ("DOCTOR".equalsIgnoreCase(role)) {
            return ResponseEntity.ok(Arrays.stream(com.medibook.api.dto.Rating.RatingSubcategoryPatient.values())
                    .map(com.medibook.api.dto.Rating.RatingSubcategoryPatient::getLabel)
                    .toList());
        }
        // When PATIENT is rating a DOCTOR, show doctor subcategories
        return ResponseEntity.ok(Arrays.stream(com.medibook.api.dto.Rating.RatingSubcategory.values())
                .map(com.medibook.api.dto.Rating.RatingSubcategory::getLabel)
                .toList());
    }
}
