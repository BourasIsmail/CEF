package ma.entraidenationale.cef.common;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Réponses d'erreur uniformes (RFC 9457 « problem details »), avec un {@code code} stable. */
@RestControllerAdvice
class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "not_found", ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException ex) {
        return problem(HttpStatus.CONFLICT, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    ProblemDetail badRequest(BadRequestException ex) {
        return problem(HttpStatus.BAD_REQUEST, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(BusinessRuleException.class)
    ProblemDetail businessRule(BusinessRuleException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    ProblemDetail unauthorized(UnauthorizedException ex) {
        return problem(HttpStatus.UNAUTHORIZED, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalid(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errors.putIfAbsent(e.getField(), e.getDefaultMessage()));
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "validation_failed", "Données invalides");
        pd.setProperty("errors", errors);
        return pd;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail unreadable(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, "malformed_request", "Requête mal formée");
    }

    @ExceptionHandler(PropertyReferenceException.class)
    ProblemDetail invalidSort(PropertyReferenceException ex) {
        return problem(HttpStatus.BAD_REQUEST, "invalid_sort", "Critère de tri inconnu : " + ex.getPropertyName());
    }

    /** Dernier filet : la base a refusé la donnée. On ne renvoie jamais le détail SQL au client. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrity(DataIntegrityViolationException ex) {
        log.warn("Violation d'intégrité : {}", ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "data_integrity", "L'opération viole une contrainte d'intégrité des données");
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setProperty("code", code);
        return pd;
    }
}
