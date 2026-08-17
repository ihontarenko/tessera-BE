package net.innoventa.tessera.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.CategoryEntityType;
import net.innoventa.tessera.dto.category.CategoryNode;
import net.innoventa.tessera.dto.category.MoveCategoryRequest;
import net.innoventa.tessera.dto.category.SaveCategoryRequest;
import net.innoventa.tessera.security.Permissions;
import net.innoventa.tessera.security.access.Scopes;
import net.innoventa.tessera.service.category.CategoryService;
import org.jmouse.access.enforcement.RequiresAccess;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * A project's category tree (TSSR-15).
 *
 * <p>The class declares the project every route is about, spelled into the path, so no
 * {@code AccessTargetResolver} is involved — the binder reads the place straight off the URL, which is
 * how almost everything in this product is gated.
 *
 * <p>⚠️ <strong>Reading takes {@code project:browse} and writing takes {@code category:manage}.</strong>
 * The tree is furniture: anybody who can see the project can see how it is organised, and the counts
 * beside each section disclose nothing the project's own screens do not. Changing the furniture is the
 * narrower act, and it is deliberately not the wiki's permission — see {@link Permissions#MANAGE_CATEGORY}.
 *
 * <p>⚠️ <strong>{@code entityType} is a query parameter with a default rather than a fixed choice.</strong>
 * The counts beside each section are per kind, and a file screen asking this same tree wants its own
 * numbers. Defaulting to {@code PAGE} keeps the wiki's call short without making the tree the wiki's.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/categories")
@RequiredArgsConstructor
@RequiresAccess(scope = Scopes.PROJECT)
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @RequiresAccess(permission = Permissions.BROWSE_PROJECT)
    public List<CategoryNode> tree(
        @PathVariable String projectId,
        @RequestParam(defaultValue = "PAGE") CategoryEntityType entityType
    ) {
        return categoryService.tree(projectId, entityType);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresAccess(permission = Permissions.MANAGE_CATEGORY)
    public CategoryNode create(@PathVariable String projectId, @Valid @RequestBody SaveCategoryRequest request) {
        return categoryService.create(projectId, request);
    }

    @PutMapping("/{categoryId}")
    @RequiresAccess(permission = Permissions.MANAGE_CATEGORY)
    public CategoryNode rename(
        @PathVariable String projectId,
        @PathVariable String categoryId,
        @Valid @RequestBody SaveCategoryRequest request
    ) {
        return categoryService.rename(projectId, categoryId, request);
    }

    /** Re-parenting and reordering, as one request — see {@link MoveCategoryRequest} for why they are one. */
    @PutMapping("/{categoryId}/position")
    @RequiresAccess(permission = Permissions.MANAGE_CATEGORY)
    public CategoryNode move(
        @PathVariable String projectId,
        @PathVariable String categoryId,
        @Valid @RequestBody MoveCategoryRequest request
    ) {
        return categoryService.move(projectId, categoryId, request);
    }

    @DeleteMapping("/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresAccess(permission = Permissions.MANAGE_CATEGORY)
    public void delete(@PathVariable String projectId, @PathVariable String categoryId) {
        categoryService.delete(projectId, categoryId);
    }

}
