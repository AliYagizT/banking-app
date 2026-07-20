package com.bank.adapter.in.web;

import com.bank.adapter.in.web.dto.CreditProductResponse;
import com.bank.application.port.in.ListCreditProductsUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/credit-products")
@Tag(name = "Credit")
public class CreditProductController {

    private final ListCreditProductsUseCase listCreditProductsUseCase;

    public CreditProductController(ListCreditProductsUseCase listCreditProductsUseCase) {
        this.listCreditProductsUseCase = listCreditProductsUseCase;
    }

    @GetMapping
    @Operation(summary = "List the credit products you can apply for")
    public List<CreditProductResponse> list() {
        return listCreditProductsUseCase.list().stream()
                .map(CreditProductResponse::from)
                .toList();
    }
}
