package com.smartlibrary.service;

import com.smartlibrary.dto.BookDto.AddCopiesRequest;
import com.smartlibrary.dto.BookDto.BookResponse;
import com.smartlibrary.dto.BookDto.CopyResponse;
import com.smartlibrary.dto.BookDto.CreateBookRequest;
import com.smartlibrary.dto.BookDto.UpdateBookRequest;
import com.smartlibrary.dto.PageResponse;
import com.smartlibrary.dto.projection.CopyCount;
import com.smartlibrary.entity.Book;
import com.smartlibrary.entity.BookCopy;
import com.smartlibrary.entity.Category;
import com.smartlibrary.entity.CopyStatus;
import com.smartlibrary.entity.LoanStatus;
import com.smartlibrary.exception.BusinessRuleException;
import com.smartlibrary.exception.ConflictException;
import com.smartlibrary.exception.NotFoundException;
import com.smartlibrary.repository.BookCopyRepository;
import com.smartlibrary.repository.BookRepository;
import com.smartlibrary.repository.CategoryRepository;
import com.smartlibrary.repository.LoanRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookService {

    private final BookRepository bookRepository;
    private final BookCopyRepository copyRepository;
    private final CategoryRepository categoryRepository;
    private final LoanRepository loanRepository;
    private final AuditService auditService;

    // ------------------------------------------------------------------ catalogue (public)

    @Transactional(readOnly = true)
    public PageResponse<BookResponse> search(String q, String author, String category, String status, Pageable pageable) {
        Page<Book> page = bookRepository.search(like(q), like(author), lower(category),
                "available".equalsIgnoreCase(status), pageable);
        return PageResponse.of(toResponses(page.getContent()), page);
    }

    @Transactional(readOnly = true)
    public BookResponse get(Long id) {
        Book book = bookRepository.findById(id).filter(b -> !b.isArchived())
                .orElseThrow(() -> new NotFoundException("Book", id));
        return toResponses(List.of(book)).get(0);
    }

    // ------------------------------------------------------------------ admin: books

    @Transactional
    public BookResponse create(CreateBookRequest req, Long actorId) {
        Category category = findCategory(req.categoryId());
        String isbn = blankToNull(req.isbn());
        if (isbn != null && bookRepository.existsByIsbn(isbn)) {
            throw new ConflictException("ISBN_EXISTS", "A book with this ISBN already exists");
        }
        Book book = new Book();
        book.setTitle(req.title().trim());
        book.setAuthor(req.author().trim());
        book.setIsbn(isbn);
        book.setCategory(category);
        book.setDescription(req.description());
        book.setCoverUrl(req.coverUrl());
        bookRepository.save(book);

        int copies = req.copies() == null ? 1 : req.copies();
        if (copies > 0) createCopies(book, copies, req.shelfLocation());
        auditService.log(actorId, "BOOK_CREATED", "BOOK", book.getId(), book.getTitle() + " copies=" + copies);
        return toResponses(List.of(book)).get(0);
    }

    @Transactional
    public BookResponse update(Long id, UpdateBookRequest req, Long actorId) {
        Book book = findBook(id);
        String isbn = blankToNull(req.isbn());
        if (isbn != null && bookRepository.existsByIsbnAndIdNot(isbn, id)) {
            throw new ConflictException("ISBN_EXISTS", "A book with this ISBN already exists");
        }
        book.setTitle(req.title().trim());
        book.setAuthor(req.author().trim());
        book.setIsbn(isbn);
        book.setCategory(findCategory(req.categoryId()));
        book.setDescription(req.description());
        book.setCoverUrl(req.coverUrl());
        auditService.log(actorId, "BOOK_UPDATED", "BOOK", id, book.getTitle());
        return toResponses(List.of(book)).get(0);
    }

    /** Soft delete. Refused while any copy is still out on loan. */
    @Transactional
    public void archive(Long id, Long actorId) {
        Book book = findBook(id);
        if (loanRepository.existsByCopy_Book_IdAndStatus(id, LoanStatus.ACTIVE)) {
            throw new ConflictException("BOOK_HAS_ACTIVE_LOANS", "Copies of this book are currently borrowed");
        }
        book.setArchived(true);
        auditService.log(actorId, "BOOK_ARCHIVED", "BOOK", id, book.getTitle());
    }

    // ------------------------------------------------------------------ admin: copies

    @Transactional(readOnly = true)
    public List<CopyResponse> listCopies(Long bookId) {
        findBook(bookId);
        return copyRepository.findByBook_IdOrderByIdAsc(bookId).stream().map(CopyResponse::from).toList();
    }

    @Transactional
    public List<CopyResponse> addCopies(Long bookId, AddCopiesRequest req, Long actorId) {
        Book book = findBook(bookId);
        List<BookCopy> created = createCopies(book, req.count(), req.shelfLocation());
        auditService.log(actorId, "COPIES_ADDED", "BOOK", bookId, "count=" + req.count());
        return created.stream().map(CopyResponse::from).toList();
    }

    /** Manual status change (AVAILABLE / DAMAGED / LOST). Borrowed copies change only through the return flow. */
    @Transactional
    public CopyResponse updateCopyStatus(Long copyId, CopyStatus target, Long actorId) {
        BookCopy copy = copyRepository.findByIdForUpdate(copyId).orElseThrow(() -> new NotFoundException("Copy", copyId));
        if (copy.getStatus() == CopyStatus.BORROWED) {
            throw new ConflictException("COPY_BORROWED", "Copy is on loan; process the return first");
        }
        if (target == CopyStatus.BORROWED) {
            throw new BusinessRuleException("INVALID_STATUS", "A copy becomes BORROWED only through a loan");
        }
        CopyStatus before = copy.getStatus();
        copy.setStatus(target);
        auditService.log(actorId, "COPY_STATUS_CHANGED", "COPY", copyId, before + " -> " + target);
        return CopyResponse.from(copy);
    }

    // ------------------------------------------------------------------ helpers

    private List<BookCopy> createCopies(Book book, int count, String shelf) {
        long existing = copyRepository.countByBook_Id(book.getId());
        List<BookCopy> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            BookCopy c = new BookCopy();
            c.setBook(book);
            c.setAccessionNo(String.format("ACC-%05d-%02d", book.getId(), existing + i));
            c.setShelfLocation(shelf);
            c.setStatus(CopyStatus.AVAILABLE);
            list.add(c);
        }
        return copyRepository.saveAll(list);
    }

    /** One grouped query for the whole page instead of N+1 count queries. */
    private List<BookResponse> toResponses(Collection<Book> books) {
        if (books.isEmpty()) return List.of();
        List<Long> ids = books.stream().map(Book::getId).toList();
        Map<Long, CopyCount> counts = copyRepository.countByBookIds(ids).stream()
                .collect(Collectors.toMap(CopyCount::bookId, Function.identity()));
        return books.stream().map(b -> {
            CopyCount c = counts.get(b.getId());
            return BookResponse.from(b, c == null ? 0 : c.total(), c == null ? 0 : c.available());
        }).toList();
    }

    private Book findBook(Long id) {
        return bookRepository.findById(id).filter(b -> !b.isArchived())
                .orElseThrow(() -> new NotFoundException("Book", id));
    }

    private Category findCategory(Long id) {
        return categoryRepository.findById(id).orElseThrow(() -> new NotFoundException("Category", id));
    }

    private static String like(String v) {
        return "%" + (v == null ? "" : v.trim().toLowerCase()) + "%";
    }

    private static String lower(String v) {
        return v == null ? "" : v.trim().toLowerCase();
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
