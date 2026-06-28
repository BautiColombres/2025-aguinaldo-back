package com.medibook.api.security;

import com.medibook.api.entity.TurnAssigned;
import com.medibook.api.entity.User;
import com.medibook.api.repository.TurnAssignedRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Custom security-expression bean ({@code @storageAuthz}) for turn-file storage
 * operations (BSEC-H-5). A patient may only upload/delete the file attached to a
 * turn they own; admins may operate on any turn.
 */
@Component("storageAuthz")
@RequiredArgsConstructor
public class StorageAuthorization {

    private final TurnAssignedRepository turnAssignedRepository;

    public boolean canManageTurnFile(Authentication authentication, UUID turnId) {
        if (authentication == null || turnId == null
                || !(authentication.getPrincipal() instanceof User principal)) {
            return false;
        }

        if ("ADMIN".equals(principal.getRole())) {
            return true;
        }

        Optional<TurnAssigned> turnOpt = turnAssignedRepository.findById(turnId);
        if (turnOpt.isEmpty()) {
            // Let the service surface a 404; ownership cannot be established here so
            // only the (non-existent) owner / admin would pass anyway.
            return false;
        }

        TurnAssigned turn = turnOpt.get();
        User turnPatient = turn.getPatient();
        return turnPatient != null
                && turnPatient.getId() != null
                && turnPatient.getId().equals(principal.getId());
    }
}
