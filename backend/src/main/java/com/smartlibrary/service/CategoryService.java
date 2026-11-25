package com.smartlibrary.service;

import com.smartlibrary.dto.CategoryDto.CategoryRequest;
import com.smartlibrary.dto.CategoryDto.CategoryResponse;
import com.smartlibrary.entity.Category;
import com.smartlibrary.exception.ConflictException;
import com.smartlibrary.exception.NotFoundException;
import com.smartlibrary.repository.CategoryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        return categoryRepository.findAllByOrderByNameAsc().stream().map(CategoryResponse::from).toList();
    }

    @Transactional
    public CategoryResponse create(CategoryRequest req) {
        if (categoryRepository.existsByNameIgnoreCase(req.name().trim())) {
            throw new ConflictException("CATEGORY_EXISTS", "Category already exists");
        }
        Category c = new Category();
        c.setName(req.name().trim());
        c.setDescription(req.description());
        return CategoryResponse.from(categoryRepository.save(c));
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest req) {
        Category c = categoryRepository.findById(id).orElseThrow(() -> new NotFoundException("Category", id));
        if (!c.getName().equalsIgnoreCase(req.name().trim()) && categoryRepository.existsByNameIgnoreCase(req.name().trim())) {
            throw new ConflictException("CATEGORY_EXISTS", "Category already exists");
        }
        c.setName(req.name().trim());
        c.setDescription(req.description());
        return CategoryResponse.from(c);
    }
}
