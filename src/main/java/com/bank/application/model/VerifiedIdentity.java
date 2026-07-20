package com.bank.application.model;

/**
 * The identity proven by a verified bearer token: the identity provider's stable user id
 * and the authenticated email. The email is the link to a {@code customer} row (roles and
 * authorization always come from the database, never from the token).
 */
public record VerifiedIdentity(String uid, String email) {
}
