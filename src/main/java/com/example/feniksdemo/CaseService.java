package com.example.feniksdemo;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CaseService {
    private final CaseRepository repository;
    private final Clock clock;

    public CaseService(CaseRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public CaseRecord create(String title) {
        return repository.create(title, CaseStatus.OPEN, clock.instant().truncatedTo(ChronoUnit.MICROS));
    }

    @Transactional
    public CaseRecord changeStatus(long id, CaseStatus status) {
        return repository.changeStatus(id, status, clock.instant().truncatedTo(ChronoUnit.MICROS));
    }

    public CaseRecord get(long id) {
        return repository.findById(id).orElseThrow(CaseNotFoundException::new);
    }
}
