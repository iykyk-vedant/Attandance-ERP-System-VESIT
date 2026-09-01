package com.vesit.openattend.service.sync;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vesit.openattend.entity.Subject;
import com.vesit.openattend.entity.WorksheetMapping;
import com.vesit.openattend.repository.SubjectRepository;
import com.vesit.openattend.repository.WorksheetMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoSheetRegistryService {

    private final SubjectRepository subjectRepository;
    private final WorksheetMappingRepository worksheetMappingRepository;
    private final ObjectMapper objectMapper;

    public record SubjectConfig(String code, String name, String worksheetTab) {}
    public record ClassSheetConfig(String className, String semester, String academicYear, String spreadsheetId, String description, List<SubjectConfig> subjects) {}

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void autoRegisterConfiguredSheets() {
        try {
            ClassPathResource resource = new ClassPathResource("sheets.json");
            if (!resource.exists()) {
                log.info("AutoSheetRegistryService: No sheets.json found in classpath.");
                return;
            }

            try (InputStream is = resource.getInputStream()) {
                List<ClassSheetConfig> configs = objectMapper.readValue(is, new TypeReference<>() {});
                log.info("AutoSheetRegistryService: Registering {} configured classrooms.", configs.size());

                for (ClassSheetConfig config : configs) {
                    if (config.spreadsheetId() == null || config.spreadsheetId().trim().isEmpty()) {
                        continue;
                    }

                    for (SubjectConfig subConfig : config.subjects()) {
                        Subject subject = subjectRepository.findByCode(subConfig.code())
                                .orElseGet(() -> subjectRepository.save(Subject.builder()
                                        .id(UUID.randomUUID().toString())
                                        .code(subConfig.code())
                                        .name(subConfig.name())
                                        .totalPlanned(45)
                                        .build()));

                        String tabName = subConfig.worksheetTab() != null ? subConfig.worksheetTab() : subConfig.name();
                        if (!worksheetMappingRepository.existsBySheetIdAndWorksheetName(config.spreadsheetId(), tabName)) {
                            Map<String, String> columnRoles = Map.of(
                                    "date", "A",
                                    "rollNo", "B",
                                    "status", "C",
                                    "faculty", "D",
                                    "remarks", "E"
                            );

                            worksheetMappingRepository.save(WorksheetMapping.builder()
                                    .id(UUID.randomUUID().toString())
                                    .sheetId(config.spreadsheetId())
                                    .worksheetName(tabName)
                                    .range("A1:Z100")
                                    .subject(subject)
                                    .columnRoles(objectMapper.writeValueAsString(columnRoles))
                                    .isActive(true)
                                    .build());

                            log.info("AutoSheetRegistryService: Registered auto-mapping for {} [{}] (Subject: {})",
                                    config.className(), tabName, subConfig.code());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("AutoSheetRegistryService: Failed to auto-register sheets.json: {}", e.getMessage());
        }
    }
}
