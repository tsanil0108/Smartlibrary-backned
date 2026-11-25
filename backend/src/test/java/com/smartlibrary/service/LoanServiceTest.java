package com.smartlibrary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlibrary.config.LibraryProperties;
import com.smartlibrary.dto.LoanDto.LoanResponse;
import com.smartlibrary.entity.Book;
import com.smartlibrary.entity.BookCopy;
import com.smartlibrary.entity.Category;
import com.smartlibrary.entity.CopyStatus;
import com.smartlibrary.entity.Loan;
import com.smartlibrary.entity.LoanStatus;
import com.smartlibrary.entity.Role;
import com.smartlibrary.entity.User;
import com.smartlibrary.entity.UserStatus;
import com.smartlibrary.exception.BusinessRuleException;
import com.smartlibrary.exception.ConflictException;
import com.smartlibrary.repository.BookCopyRepository;
import com.smartlibrary.repository.BookRepository;
import com.smartlibrary.repository.LoanRepository;
import com.smartlibrary.repository.UserRepository;
import com.smartlibrary.security.UserPrincipal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoanServiceTest {

    @Mock LoanRepository loanRepository;
    @Mock BookCopyRepository copyRepository;
    @Mock BookRepository bookRepository;
    @Mock UserRepository userRepository;
    @Mock MembershipService membershipService;
    @Mock AuditService auditService;

    final LibraryProperties rules = new LibraryProperties(14, 3, 1, 7, true, 30);
    final Clock clock = Clock.fixed(Instant.parse("2026-11-25T06:00:00Z"), ZoneId.of("Asia/Kolkata"));

    LoanService service;
    User member;
    Book book;
    BookCopy copy;

    @BeforeEach
    void setUp() {
        service = new LoanService(loanRepository, copyRepository, bookRepository, userRepository,
                membershipService, auditService, rules, clock);

        member = new User();
        member.setId(1L);
        member.setName("Demo Member");
        member.setEmail("member@library.com");
        member.setPasswordHash("x");
        member.setRole(Role.MEMBER);
        member.setStatus(UserStatus.ACTIVE);

        Category cat = new Category();
        cat.setId(1L);
        cat.setName("Tech");
        book = new Book();
        book.setId(5L);
        book.setTitle("Clean Code");
        book.setCategory(cat);

        copy = new BookCopy();
        copy.setId(9L);
        copy.setBook(book);
        copy.setAccessionNo("ACC-00005-01");
        copy.setStatus(CopyStatus.AVAILABLE);

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(member));
        when(bookRepository.findById(5L)).thenReturn(Optional.of(book));
        when(loanRepository.countByUser_IdAndStatus(1L, LoanStatus.ACTIVE)).thenReturn(0L);
        when(copyRepository.findAvailableForUpdate(anyLong(), any(Pageable.class))).thenReturn(List.of(copy));
        when(loanRepository.save(any(Loan.class))).thenAnswer(inv -> {
            Loan l = inv.getArgument(0);
            l.setId(100L);
            return l;
        });
    }

    @Test
    void borrow_success_createsLoanAndMarksCopyBorrowed() {
        LoanResponse res = service.borrow(1L, 5L, null);

        assertThat(copy.getStatus()).isEqualTo(CopyStatus.BORROWED);
        assertThat(res.status()).isEqualTo(LoanStatus.ACTIVE);
        assertThat(res.issueDate()).isEqualTo(LocalDate.of(2026, 11, 25));
        assertThat(res.dueDate()).isEqualTo(LocalDate.of(2026, 12, 9));
    }

    @Test
    void borrow_expiredMembership_isRejectedAndInventoryUntouched() {
        when(membershipService.requireActiveMembership(1L, LocalDate.of(2026, 11, 25)))
                .thenThrow(new BusinessRuleException("MEMBERSHIP_INACTIVE", "expired"));

        assertThatThrownBy(() -> service.borrow(1L, 5L, null)).isInstanceOf(BusinessRuleException.class);

        assertThat(copy.getStatus()).isEqualTo(CopyStatus.AVAILABLE);
        verify(copyRepository, never()).findAvailableForUpdate(anyLong(), any(Pageable.class));
        verify(loanRepository, never()).save(any(Loan.class));
    }

    @Test
    void borrow_noAvailableCopy_isConflict() {
        when(copyRepository.findAvailableForUpdate(anyLong(), any(Pageable.class))).thenReturn(List.of());

        assertThatThrownBy(() -> service.borrow(1L, 5L, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("No copy");
    }

    @Test
    void borrow_limitReached_isRejected() {
        when(loanRepository.countByUser_IdAndStatus(1L, LoanStatus.ACTIVE)).thenReturn(3L);

        assertThatThrownBy(() -> service.borrow(1L, 5L, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("limit");
    }

    @Test
    void borrow_duplicateActiveLoan_isConflict() {
        when(loanRepository.existsByUser_IdAndCopy_Book_IdAndStatus(1L, 5L, LoanStatus.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> service.borrow(1L, 5L, null)).isInstanceOf(ConflictException.class);
    }

    @Test
    void return_success_thenSecondReturnIsRejected() {
        copy.setStatus(CopyStatus.BORROWED);
        Loan loan = new Loan();
        loan.setId(100L);
        loan.setUser(member);
        loan.setCopy(copy);
        loan.setIssueDate(LocalDate.of(2026, 11, 25));
        loan.setDueDate(LocalDate.of(2026, 12, 9));
        loan.setStatus(LoanStatus.ACTIVE);
        when(loanRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(loan));

        UserPrincipal actor = UserPrincipal.from(member);
        LoanResponse res = service.returnLoan(100L, actor, null);

        assertThat(res.status()).isEqualTo(LoanStatus.RETURNED);
        assertThat(copy.getStatus()).isEqualTo(CopyStatus.AVAILABLE);
        assertThat(loan.getReturnedAt()).isNotNull();

        assertThatThrownBy(() -> service.returnLoan(100L, actor, null)).isInstanceOf(ConflictException.class);
    }
}
