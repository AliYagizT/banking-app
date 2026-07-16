package com.bank.adapter.in.web;

import com.bank.adapter.in.web.dto.CreateCustomerRequest;
import com.bank.adapter.in.web.dto.CustomerResponse;
import com.bank.application.port.in.GetCustomerUseCase;
import com.bank.application.port.in.RegisterCustomerUseCase;
import com.bank.domain.exception.NotFoundException;
import com.bank.domain.model.Customer;
import com.bank.infrastructure.security.CustomerPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/customers")
@Tag(name = "Customers")
public class CustomerController {

    private final RegisterCustomerUseCase registerCustomerUseCase;
    private final GetCustomerUseCase getCustomerUseCase;

    public CustomerController(RegisterCustomerUseCase registerCustomerUseCase,
                             GetCustomerUseCase getCustomerUseCase) {
        this.registerCustomerUseCase = registerCustomerUseCase;
        this.getCustomerUseCase = getCustomerUseCase;
    }

    @PostMapping
    @Operation(summary = "Create your customer profile (email comes from your verified token)")
    public ResponseEntity<CustomerResponse> create(@AuthenticationPrincipal CustomerPrincipal principal,
                                                   @Valid @RequestBody CreateCustomerRequest request,
                                                   UriComponentsBuilder uriBuilder) {
        // The email is taken from the verified token, never the request body, so a caller
        // cannot register a profile for an address they don't control.
        Customer customer = registerCustomerUseCase.register(request.fullName(), principal.getEmail());
        URI location = uriBuilder.path("/api/customers/{id}").buildAndExpand(customer.getId()).toUri();
        return ResponseEntity.created(location).body(CustomerResponse.from(customer));
    }

    @GetMapping("/me")
    @Operation(summary = "Fetch the authenticated customer's own record (incl. role)")
    public CustomerResponse me(@AuthenticationPrincipal CustomerPrincipal principal) {
        // Resolves the caller's identity (and role) straight from their Basic credentials,
        // so a client never has to know its own id up front.
        return CustomerResponse.from(getCustomerUseCase.getCustomer(principal.getCustomerId()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch your own customer record")
    public CustomerResponse get(@PathVariable Long id,
                                @AuthenticationPrincipal CustomerPrincipal principal) {
        // A customer may only read their own record; hide others as "not found".
        if (!principal.getCustomerId().equals(id)) {
            throw NotFoundException.customer(id);
        }
        return CustomerResponse.from(getCustomerUseCase.getCustomer(id));
    }
}
