package com.example.feniksdemo;

import java.net.URI;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cases")
public class CaseController {
    private final CaseService service;

    public CaseController(CaseService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<CaseResponse> create(@Valid @RequestBody CreateCaseRequest request) {
        var response = CaseResponse.from(service.create(request.title()));
        return ResponseEntity.created(URI.create("/api/cases/" + response.id())).body(response);
    }

    @PatchMapping("/{id}/status")
    public CaseResponse changeStatus(@PathVariable long id, @Valid @RequestBody ChangeStatusRequest request) {
        if (id <= 0) {
            throw new IllegalArgumentException("Invalid case ID");
        }
        return CaseResponse.from(service.changeStatus(id, request.status()));
    }

    @GetMapping("/{id}")
    public CaseResponse get(@PathVariable long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("Invalid case ID");
        }
        return CaseResponse.from(service.get(id));
    }
}
