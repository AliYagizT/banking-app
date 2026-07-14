package com.bank.application.port.in;

import com.bank.domain.model.CreditProduct;

import java.util.List;

/** List the credit products a customer can apply for. */
public interface ListCreditProductsUseCase {

    List<CreditProduct> list();
}
