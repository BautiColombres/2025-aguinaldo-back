package com.medibook.api.exception;

/**
 * Thrown when a refresh-token rotation is refused because the owning account is no longer
 * sign-in-eligible (REJECTED / DISABLED). A PENDING doctor IS eligible and never triggers this.
 *
 * <p>Extends {@link IllegalArgumentException} so the existing {@code AuthController} catch blocks
 * keep mapping it to <b>401</b> with a generic message (the caller is never told their account
 * state).
 *
 * <p>It exists as a DEDICATED type so {@code AuthServiceImpl#refreshToken} can scope its
 * {@code noRollbackFor} to exactly this refusal path — the one that must REVOKE the token family
 * and then throw, with the revocation surviving the throw. Every other
 * {@link IllegalArgumentException} in that method keeps conventional rollback-on-error semantics.
 */
public class AccountNotEligibleException extends IllegalArgumentException {
    public AccountNotEligibleException(String message) {
        super(message);
    }
}
