package com.SIH.mark1.ai.validation;

import com.SIH.mark1.repository.CategoryRepository;
import org.springframework.stereotype.Component;

@Component
public class CategoryValidator {

    private final CategoryRepository categoryRepository;

    public CategoryValidator(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public String validate(String category) {
        if (category == null || category.isBlank()) {
            return "UNKNOWN";
        }
        return categoryRepository.findByCategoryName(category).isPresent() ? category : "UNKNOWN";
    }
}
