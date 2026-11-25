package com.smartlibrary.dto;

import com.smartlibrary.entity.Category;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class CategoryDto {
    private CategoryDto() {}

    public record CategoryRequest(@NotBlank @Size(max = 100) String name, @Size(max = 500) String description) {}

    public record CategoryResponse(Long id, String name, String description) {
        public static CategoryResponse from(Category c) {
            return new CategoryResponse(c.getId(), c.getName(), c.getDescription());
        }
    }
}
