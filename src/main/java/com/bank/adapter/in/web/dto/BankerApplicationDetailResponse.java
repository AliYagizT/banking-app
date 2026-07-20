package com.bank.adapter.in.web.dto;

import com.bank.application.model.UnderwritingAssessment;
import com.bank.domain.model.CreditApplication;

/** The banker's view of an application: the application itself plus its underwriting metrics. */
public record BankerApplicationDetailResponse(
        CreditApplicationResponse application,
        UnderwritingAssessmentResponse assessment) {

    public static BankerApplicationDetailResponse of(CreditApplication application,
                                                     UnderwritingAssessment assessment) {
        return new BankerApplicationDetailResponse(
                CreditApplicationResponse.from(application),
                UnderwritingAssessmentResponse.from(assessment));
    }
}
