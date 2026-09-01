package com.vesit.openattend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "worksheet_mappings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorksheetMapping {

    @Id
    @Column(length = 36)
    private String id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false, unique = true)
    private Subject subject;

    @Column(name = "sheet_id", nullable = false)
    private String sheetId;

    @Column(name = "worksheet_name", nullable = false)
    private String worksheetName;

    @Column(nullable = false, length = 64)
    private String range;

    @Column(name = "column_roles", nullable = false, columnDefinition = "TEXT")
    private String columnRoles;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "worksheetMapping", cascade = CascadeType.ALL)
    @Builder.Default
    private List<SyncLog> syncLogs = new ArrayList<>();
}
