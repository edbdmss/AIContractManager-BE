package com.aicontractmanager.auth.controller;

import com.aicontractmanager.auth.service.AccountDeletionService;
import com.aicontractmanager.auth.dto.UserProfileResponse;
import com.aicontractmanager.auth.repository.OAuthUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/aicontract/auth/me")
public class AccountController {
    private final AccountDeletionService accountDeletionService;
    private final OAuthUserRepository users;

    public AccountController(AccountDeletionService accountDeletionService, OAuthUserRepository users) {
        this.accountDeletionService = accountDeletionService;
        this.users = users;
    }

    @GetMapping
    public UserProfileResponse getProfile(Principal principal) {
        var user = users.findActive(UUID.fromString(principal.getName()));
        return new UserProfileResponse(user.id(), user.email(), user.name(), user.provider());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(Principal principal) {
        accountDeletionService.delete(UUID.fromString(principal.getName()));
    }
}
