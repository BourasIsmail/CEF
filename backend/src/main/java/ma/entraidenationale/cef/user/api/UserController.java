package ma.entraidenationale.cef.user.api;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.entraidenationale.cef.common.PageResponse;
import ma.entraidenationale.cef.security.AppRole;
import ma.entraidenationale.cef.user.api.UserDtos.CreateRequest;
import ma.entraidenationale.cef.user.api.UserDtos.EnabledRequest;
import ma.entraidenationale.cef.user.api.UserDtos.ResetPasswordRequest;
import ma.entraidenationale.cef.user.api.UserDtos.UpdateRequest;
import ma.entraidenationale.cef.user.api.UserDtos.View;
import ma.entraidenationale.cef.user.service.UserService;

/** Gestion des comptes : réservée à l'administrateur. */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Utilisateurs")
class UserController {

    private final UserService users;

    @GetMapping
    PageResponse<View> list(@RequestParam(required = false) String search,
                            @RequestParam(required = false) AppRole role,
                            @RequestParam(required = false) Boolean enabled,
                            @PageableDefault(size = 20, sort = "username") Pageable pageable) {
        return users.list(search, role, enabled, pageable);
    }

    @GetMapping("/{id}")
    View get(@PathVariable Long id) {
        return users.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    View create(@Valid @RequestBody CreateRequest req) {
        return users.create(req);
    }

    @PutMapping("/{id}")
    View update(@PathVariable Long id, @Valid @RequestBody UpdateRequest req) {
        return users.update(id, req);
    }

    @PutMapping("/{id}/enabled")
    View setEnabled(@PathVariable Long id, @Valid @RequestBody EnabledRequest req) {
        return users.setEnabled(id, req.enabled());
    }

    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest req) {
        users.resetPassword(id, req.newPassword());
    }
}
