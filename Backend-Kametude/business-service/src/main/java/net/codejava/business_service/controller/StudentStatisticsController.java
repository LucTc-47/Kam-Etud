package net.codejava.business_service.controller;

import lombok.RequiredArgsConstructor;
import net.codejava.business_service.dto.StudentStatisticsResponse;
import net.codejava.business_service.service.StudentStatisticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/student-stats")
@RequiredArgsConstructor
public class StudentStatisticsController {
    private final StudentStatisticsService statisticsService;

    @GetMapping("/{studentId}")
    public ResponseEntity<StudentStatisticsResponse> get(@PathVariable UUID studentId) {
        return ResponseEntity.ok(statisticsService.get(studentId));
    }
}
