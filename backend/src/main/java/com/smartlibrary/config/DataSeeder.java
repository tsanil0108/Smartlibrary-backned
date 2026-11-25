package com.smartlibrary.config;

import com.smartlibrary.dto.BookDto.CreateBookRequest;
import com.smartlibrary.entity.Category;
import com.smartlibrary.entity.Role;
import com.smartlibrary.entity.User;
import com.smartlibrary.entity.UserStatus;
import com.smartlibrary.repository.BookRepository;
import com.smartlibrary.repository.CategoryRepository;
import com.smartlibrary.repository.UserRepository;
import com.smartlibrary.service.BookService;
import com.smartlibrary.service.MembershipService;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.seed",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class DataSeeder implements ApplicationRunner {

    private final AppProperties props;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final BookRepository bookRepository;
    private final BookService bookService;
    private final MembershipService membershipService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.reset-admin-password:false}")
    private boolean resetAdminPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {

        seedUsers();
        seedCatalogue();
    }

    // ==========================================
    // SEED ADMIN AND DEMO MEMBER
    // ==========================================

    private void seedUsers() {

        String adminEmail = props.seed().adminEmail();
        String adminPassword = props.seed().adminPassword();

        if (!userRepository.existsByEmailIgnoreCase(adminEmail)) {

            User admin = user(
                    "Library Admin",
                    adminEmail,
                    adminPassword,
                    Role.ADMIN
            );

            userRepository.save(admin);

            log.info("Seeded admin account: {}", adminEmail);

        } else if (resetAdminPassword) {

            User admin = userRepository
                    .findByEmailIgnoreCase(adminEmail)
                    .orElseThrow(() ->
                            new IllegalStateException(
                                    "Admin account not found"
                            )
                    );

            if (admin.getRole() != Role.ADMIN) {
                throw new IllegalStateException(
                        "Configured email does not belong to an admin"
                );
            }

            admin.setPasswordHash(
                    passwordEncoder.encode(adminPassword)
            );

            userRepository.save(admin);

            log.info(
                    "Admin password reset successfully: {}",
                    adminEmail
            );
        }

        // Demo member account
        if (!userRepository.existsByEmailIgnoreCase(
                "member@library.com"
        )) {

            User member = userRepository.save(
                    user(
                            "Demo Member",
                            "member@library.com",
                            "Member@12345",
                            Role.MEMBER
                    )
            );

            membershipService.createInitial(
                    member,
                    "STANDARD",
                    365
            );

            log.info(
                    "Seeded demo member: member@library.com"
            );
        }
    }

    // ==========================================
    // SEED BOOK CATALOGUE
    // ==========================================

    private void seedCatalogue() {

        if (bookRepository.count() > 0) {
            return;
        }

        Category fiction = category(
                "Fiction",
                "Novels and short stories"
        );

        Category science = category(
                "Science",
                "Popular science and research"
        );

        Category tech = category(
                "Technology",
                "Software and engineering"
        );

        Category history = category(
                "History",
                "World and Indian history"
        );

        List<CreateBookRequest> books = List.of(

                book(
                        "The Alchemist",
                        "Paulo Coelho",
                        "9780062315007",
                        fiction,
                        3,
                        "A-01"
                ),

                book(
                        "A Brief History of Time",
                        "Stephen Hawking",
                        "9780553380163",
                        science,
                        2,
                        "B-04"
                ),

                book(
                        "Clean Code",
                        "Robert C. Martin",
                        "9780132350884",
                        tech,
                        2,
                        "C-02"
                ),

                book(
                        "Effective Java",
                        "Joshua Bloch",
                        "9780134685991",
                        tech,
                        1,
                        "C-03"
                ),

                book(
                        "India After Gandhi",
                        "Ramachandra Guha",
                        "9780060958589",
                        history,
                        2,
                        "D-07"
                )
        );

        books.forEach(
                b -> bookService.create(b, null)
        );

        log.info(
                "Seeded {} demo books",
                books.size()
        );
    }

    // ==========================================
    // CREATE USER
    // ==========================================

    private User user(
            String name,
            String email,
            String rawPassword,
            Role role
    ) {

        User u = new User();

        u.setName(name);
        u.setEmail(email.toLowerCase());

        u.setPasswordHash(
                passwordEncoder.encode(rawPassword)
        );

        u.setRole(role);
        u.setStatus(UserStatus.ACTIVE);

        return u;
    }

    // ==========================================
    // CREATE CATEGORY
    // ==========================================

    private Category category(
            String name,
            String description
    ) {

        Category c = new Category();

        c.setName(name);
        c.setDescription(description);

        return categoryRepository.save(c);
    }

    // ==========================================
    // CREATE BOOK REQUEST
    // ==========================================

    private CreateBookRequest book(
            String title,
            String author,
            String isbn,
            Category cat,
            int copies,
            String shelf
    ) {

        return new CreateBookRequest(
                title,
                author,
                isbn,
                cat.getId(),
                null,
                null,
                copies,
                shelf
        );
    }
}