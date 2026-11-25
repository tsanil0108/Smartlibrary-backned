package com.smartlibrary.controller;

import com.smartlibrary.dto.BookDto.AddCopiesRequest;
import com.smartlibrary.dto.BookDto.BookResponse;
import com.smartlibrary.dto.BookDto.CopyResponse;
import com.smartlibrary.dto.BookDto.CreateBookRequest;
import com.smartlibrary.dto.BookDto.UpdateBookRequest;
import com.smartlibrary.dto.BookDto.UpdateCopyStatusRequest;
import com.smartlibrary.security.UserPrincipal;
import com.smartlibrary.service.BookService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminBookController {

    private final BookService bookService;

    @PostMapping("/books")
    @ResponseStatus(HttpStatus.CREATED)
    public BookResponse create(@Valid @RequestBody CreateBookRequest request, @AuthenticationPrincipal UserPrincipal me) {
        return bookService.create(request, me.getId());
    }

    @PutMapping("/books/{id}")
    public BookResponse update(@PathVariable Long id, @Valid @RequestBody UpdateBookRequest request,
                               @AuthenticationPrincipal UserPrincipal me) {
        return bookService.update(id, request, me.getId());
    }

    @DeleteMapping("/books/{id}")                            // archive (soft delete)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal me) {
        bookService.archive(id, me.getId());
    }

    @GetMapping("/books/{id}/copies")
    public List<CopyResponse> copies(@PathVariable Long id) {
        return bookService.listCopies(id);
    }

    @PostMapping("/books/{id}/copies")
    @ResponseStatus(HttpStatus.CREATED)
    public List<CopyResponse> addCopies(@PathVariable Long id, @Valid @RequestBody AddCopiesRequest request,
                                        @AuthenticationPrincipal UserPrincipal me) {
        return bookService.addCopies(id, request, me.getId());
    }

    @PatchMapping("/copies/{copyId}/status")
    public CopyResponse updateCopyStatus(@PathVariable Long copyId, @Valid @RequestBody UpdateCopyStatusRequest request,
                                         @AuthenticationPrincipal UserPrincipal me) {
        return bookService.updateCopyStatus(copyId, request.status(), me.getId());
    }
}
