package ma.entraidenationale.cef.training.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.entraidenationale.cef.training.api.TrainingDtos.ProgramView;
import ma.entraidenationale.cef.training.api.TrainingDtos.SeasonRequest;
import ma.entraidenationale.cef.training.api.TrainingDtos.SeasonView;
import ma.entraidenationale.cef.training.service.SeasonService;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Saisons et programmes", description = "الموسم التكويني")
class SeasonController {

    private final SeasonService seasons;

    @GetMapping("/programs")
    List<ProgramView> programs() {
        return seasons.programs();
    }

    @GetMapping("/seasons")
    List<SeasonView> list() {
        return seasons.list();
    }

    @GetMapping("/seasons/current")
    SeasonView current() {
        return seasons.current();
    }

    @PostMapping("/seasons")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    SeasonView create(@Valid @RequestBody SeasonRequest req) {
        return seasons.create(req);
    }

    @PutMapping("/seasons/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    SeasonView update(@PathVariable Long id, @Valid @RequestBody SeasonRequest req) {
        return seasons.update(id, req);
    }

    @PutMapping("/seasons/{id}/current")
    @PreAuthorize("hasRole('ADMIN')")
    SeasonView makeCurrent(@PathVariable Long id) {
        return seasons.makeCurrent(id);
    }
}
