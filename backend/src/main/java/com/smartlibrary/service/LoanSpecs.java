package com.smartlibrary.service;

import com.smartlibrary.entity.Loan;
import com.smartlibrary.entity.LoanStatus;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public final class LoanSpecs {
    private LoanSpecs() {}

    /** All filters are optional; null means "do not filter". */
    public static Specification<Loan> filter(Long userId, LoanStatus status, Boolean overdue, LocalDate today) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (userId != null) predicates.add(cb.equal(root.get("user").get("id"), userId));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (Boolean.TRUE.equals(overdue)) {
                predicates.add(cb.equal(root.get("status"), LoanStatus.ACTIVE));
                predicates.add(cb.lessThan(root.get("dueDate"), today));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
