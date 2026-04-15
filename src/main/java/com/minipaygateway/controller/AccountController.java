package com.minipaygateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.minipaygateway.dto.request.CreateAccountRequest;
import com.minipaygateway.dto.response.AccountBalanceResponse;
import com.minipaygateway.dto.response.CreateAccountResponse;
import com.minipaygateway.service.AccountService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

	private final AccountService accountService;

	public AccountController(AccountService accountService) {
		this.accountService = accountService;
	}

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<CreateAccountResponse> create(@RequestBody @Valid CreateAccountRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(accountService.create(request));
	}

	@GetMapping("/{id}/balance")
	@PreAuthorize("hasAnyRole('MERCHANT','ADMIN')")
	public AccountBalanceResponse balance(@PathVariable long id) {
		return accountService.getBalance(id);
	}
}
