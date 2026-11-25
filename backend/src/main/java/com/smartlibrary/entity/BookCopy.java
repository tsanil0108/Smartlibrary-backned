package com.smartlibrary.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "book_copies",
        uniqueConstraints = @UniqueConstraint(name = "uk_copies_accession", columnNames = "accession_no"),
        indexes = @Index(name = "idx_copies_book_status", columnList = "book_id,status"))
@Getter @Setter @NoArgsConstructor
public class BookCopy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(name = "accession_no", nullable = false, length = 40)
    private String accessionNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CopyStatus status = CopyStatus.AVAILABLE;

    @Column(name = "shelf_location", length = 60)
    private String shelfLocation;

    @Version
    private Long version;
}
