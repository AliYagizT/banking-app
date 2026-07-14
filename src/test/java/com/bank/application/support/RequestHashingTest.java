package com.bank.application.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestHashingTest {

    @Test
    void sameInputsProduceSameHash() {
        String first = RequestHashing.sha256Hex("DEPOSIT", 1L, "10.00");
        String second = RequestHashing.sha256Hex("DEPOSIT", 1L, "10.00");
        assertThat(first).isEqualTo(second);
    }

    @Test
    void differentInputsProduceDifferentHash() {
        String tenDollars = RequestHashing.sha256Hex("DEPOSIT", 1L, "10.00");
        String elevenDollars = RequestHashing.sha256Hex("DEPOSIT", 1L, "11.00");
        assertThat(tenDollars).isNotEqualTo(elevenDollars);
    }

    @Test
    void producesSixtyFourCharLowercaseHex() {
        String hash = RequestHashing.sha256Hex("TRANSFER", 1L, 2L, "5.00");
        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void orderOfComponentsMatters() {
        // A transfer 1->2 must hash differently from 2->1.
        assertThat(RequestHashing.sha256Hex("TRANSFER", 1L, 2L, "5.00"))
                .isNotEqualTo(RequestHashing.sha256Hex("TRANSFER", 2L, 1L, "5.00"));
    }
}
