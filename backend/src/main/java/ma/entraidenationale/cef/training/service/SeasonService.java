package ma.entraidenationale.cef.training.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ma.entraidenationale.cef.common.BusinessRuleException;
import ma.entraidenationale.cef.common.ConflictException;
import ma.entraidenationale.cef.common.NotFoundException;
import ma.entraidenationale.cef.training.api.TrainingDtos.ProgramView;
import ma.entraidenationale.cef.training.api.TrainingDtos.SeasonRequest;
import ma.entraidenationale.cef.training.api.TrainingDtos.SeasonView;
import ma.entraidenationale.cef.training.domain.ProgramRepository;
import ma.entraidenationale.cef.training.domain.TrainingSeason;
import ma.entraidenationale.cef.training.domain.TrainingSeasonRepository;

/** Saisons de formation (الموسم التكويني) et programmes. */
@Service
@RequiredArgsConstructor
public class SeasonService {

    private final TrainingSeasonRepository seasons;
    private final ProgramRepository programs;

    @Transactional(readOnly = true)
    public List<ProgramView> programs() {
        return programs.findAll().stream().map(p -> new ProgramView(p.getId(), p.getCode(), p.getNameAr(), p.getNameFr())).toList();
    }

    @Transactional(readOnly = true)
    public List<SeasonView> list() {
        return seasons.findAllByOrderByStartDateDesc().stream().map(SeasonService::toView).toList();
    }

    @Transactional(readOnly = true)
    public SeasonView current() {
        return seasons.findByCurrentTrue().map(SeasonService::toView)
                .orElseThrow(() -> new NotFoundException("Aucune saison courante n'est définie"));
    }

    @Transactional
    public SeasonView create(SeasonRequest req) {
        String label = req.label().trim();
        validate(label, req);
        if (seasons.existsByLabel(label)) {
            throw new ConflictException("season.label_exists", "Cette saison existe déjà");
        }
        TrainingSeason s = new TrainingSeason();
        s.setLabel(label);
        s.setStartDate(req.startDate());
        s.setEndDate(req.endDate());
        return toView(seasons.saveAndFlush(s));
    }

    @Transactional
    public SeasonView update(Long id, SeasonRequest req) {
        TrainingSeason s = find(id);
        String label = req.label().trim();
        validate(label, req);
        if (!label.equals(s.getLabel()) && seasons.existsByLabel(label)) {
            throw new ConflictException("season.label_exists", "Cette saison existe déjà");
        }
        s.setLabel(label);
        s.setStartDate(req.startDate());
        s.setEndDate(req.endDate());
        return toView(seasons.saveAndFlush(s));
    }

    /** Désigne la saison courante (une seule à la fois). */
    @Transactional
    public SeasonView makeCurrent(Long id) {
        find(id);
        seasons.clearCurrent();
        TrainingSeason s = find(id);
        s.setCurrent(true);
        return toView(seasons.saveAndFlush(s));
    }

    private TrainingSeason find(Long id) {
        return seasons.findById(id).orElseThrow(() -> NotFoundException.of("Saison", id));
    }

    private static void validate(String label, SeasonRequest req) {
        int from = Integer.parseInt(label.substring(0, 4));
        int to = Integer.parseInt(label.substring(5));
        if (to != from + 1) {
            throw new BusinessRuleException("season.label_invalid", "Les deux années doivent être consécutives (ex. 2025-2026)");
        }
        if (!req.endDate().isAfter(req.startDate())) {
            throw new BusinessRuleException("season.dates_invalid", "La date de fin doit suivre la date de début");
        }
    }

    private static SeasonView toView(TrainingSeason s) {
        return new SeasonView(s.getId(), s.getLabel(), s.getStartDate(), s.getEndDate(), s.isCurrent());
    }
}
