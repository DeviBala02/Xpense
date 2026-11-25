package com.budgetbee.web;

import com.budgetbee.model.Category;
import com.budgetbee.repo.CategoryRepository;
import com.budgetbee.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryRepository categories;

    public CategoryController(CategoryRepository categories) {
        this.categories = categories;
    }

    @GetMapping
    public List<Category> all() {
        return categories.findByUserId(CurrentUser.id());
    }

    @PostMapping
    public Category create(@RequestBody Category c) {
        Long uid = CurrentUser.id();
        if (c.name == null || c.name.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required");
        if (categories.findByUserIdAndNameIgnoreCase(uid, c.name.trim()).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A category with this name already exists");
        c.id = null;
        c.userId = uid;
        c.name = c.name.trim();
        c.keywords = clean(c.keywords);
        if (c.icon == null || c.icon.isBlank()) c.icon = "🏷️";
        return categories.save(c);
    }

    @PutMapping("/{id}")
    public Category update(@PathVariable Long id, @RequestBody Category in) {
        Category c = mine(id);
        if (in.name != null && !in.name.isBlank()) c.name = in.name.trim();
        if (in.icon != null && !in.icon.isBlank()) c.icon = in.icon;
        c.keywords = clean(in.keywords);
        return categories.save(c);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        Category c = mine(id);
        if ("others".equalsIgnoreCase(c.name))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The Others category is the fallback and cannot be deleted");
        try {
            categories.delete(c);
            categories.flush();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This category has expenses or budgets attached, so it cannot be deleted");
        }
    }

    private Category mine(Long id) {
        return categories.findByIdAndUserId(id, CurrentUser.id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
    }

    private String clean(String keywords) {
        if (keywords == null) return "";
        return java.util.Arrays.stream(keywords.toLowerCase().split("[,\\n]"))
            .map(String::trim).filter(s -> !s.isEmpty()).distinct()
            .collect(java.util.stream.Collectors.joining(","));
    }
}
