package com.ismile.core.docs.service;

import com.ismile.core.docs.model.CategoryDocument;
import com.ismile.core.docs.model.DocumentationDocument;
import com.ismile.core.docs.repository.CategoryRepository;
import com.ismile.core.docs.repository.DocumentationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class DocsDomainService {

        public static final String ROOT_CATEGORY_ID = "root";

        private final CategoryRepository categoryRepository;
        private final DocumentationRepository documentationRepository;
        private final ElasticsearchOperations elasticsearchOperations;

        public CategoryDocument createCategory(String name, String parentId) {
                if (!StringUtils.hasText(name)) {
                        throw new IllegalArgumentException("Category name must not be empty");
                }

                String normalizedParentId = normalizeParentId(parentId);
                if (!ROOT_CATEGORY_ID.equals(normalizedParentId)) {
                        categoryRepository.findById(normalizedParentId)
                                .orElseThrow(() -> new NoSuchElementException("Parent category not found"));
                }

                CategoryDocument category = CategoryDocument.builder()
                        .id(UUID.randomUUID().toString())
                        .name(name.trim())
                        .parentId(normalizedParentId)
                        .build();
                CategoryDocument saved = categoryRepository.save(category);
                log.info("Created category [{}] with parent [{}]", saved.getId(), saved.getParentId());
                return saved;
        }

        public CategoryDocument getCategory(String categoryId) {
                return categoryRepository.findById(categoryId)
                        .orElseThrow(() -> new NoSuchElementException("Category not found"));
        }

        public List<CategoryDocument> listSubCategories(String parentId) {
                return categoryRepository.findByParentId(normalizeParentId(parentId));
        }

        public List<CategoryNode> getCategoryTree() {
                List<CategoryDocument> roots = categoryRepository.findByParentId(ROOT_CATEGORY_ID);
                return roots.stream()
                        .map(this::buildCategoryNode)
                        .toList();
        }

        private CategoryNode buildCategoryNode(CategoryDocument category) {
                List<CategoryDocument> children = categoryRepository.findByParentId(category.getId());
                List<CategoryNode> childNodes = children.stream()
                        .map(this::buildCategoryNode)
                        .toList();
                return new CategoryNode(category, childNodes);
        }

        public DocumentationDocument createDocumentation(String title, String content, String categoryId) {
                if (!StringUtils.hasText(title)) {
                        throw new IllegalArgumentException("Document title must not be empty");
                }
                if (!StringUtils.hasText(content)) {
                        throw new IllegalArgumentException("Document content must not be empty");
                }

                String normalizedCategoryId = normalizeParentId(categoryId);
                if (!ROOT_CATEGORY_ID.equals(normalizedCategoryId)) {
                        categoryRepository.findById(normalizedCategoryId)
                                .orElseThrow(() -> new NoSuchElementException("Category not found"));
                }

                DocumentationDocument document = DocumentationDocument.builder()
                        .id(UUID.randomUUID().toString())
                        .title(title.trim())
                        .content(content)
                        .categoryId(normalizedCategoryId)
                        .build();
                DocumentationDocument saved = documentationRepository.save(document);
                log.info("Created document [{}] in category [{}]", saved.getId(), saved.getCategoryId());
                return saved;
        }

        public DocumentationDocument updateDocumentation(String documentId, String title, String content) {
                DocumentationDocument existing = documentationRepository.findById(documentId)
                        .orElseThrow(() -> new NoSuchElementException("Document not found"));

                if (StringUtils.hasText(title)) {
                        existing.setTitle(title.trim());
                }
                if (StringUtils.hasText(content)) {
                        existing.setContent(content);
                }

                DocumentationDocument saved = documentationRepository.save(existing);
                log.info("Updated document [{}]", saved.getId());
                return saved;
        }

        public DocumentationDocument getDocumentation(String documentId) {
                return documentationRepository.findById(documentId)
                        .orElseThrow(() -> new NoSuchElementException("Document not found"));
        }

        public List<DocumentationDocument> listDocumentsByCategory(String categoryId) {
                return documentationRepository.findByCategoryId(normalizeParentId(categoryId));
        }

        public void deleteDocumentation(String documentId) {
                try {
                        documentationRepository.deleteById(documentId);
                        log.info("Deleted document [{}]", documentId);
                } catch (EmptyResultDataAccessException ex) {
                        throw new NoSuchElementException("Document not found");
                }
        }

        public void deleteCategoryRecursively(String categoryId) {
                CategoryDocument category = categoryRepository.findById(categoryId)
                        .orElseThrow(() -> new NoSuchElementException("Category not found"));

                // Delete documents in this category
                List<DocumentationDocument> documents = documentationRepository.findByCategoryId(category.getId());
                documents.forEach(doc -> documentationRepository.deleteById(doc.getId()));

                // Recursively delete child categories
                List<CategoryDocument> children = categoryRepository.findByParentId(category.getId());
                for (CategoryDocument child : children) {
                        deleteCategoryRecursively(child.getId());
                }

                categoryRepository.deleteById(category.getId());
                log.info("Deleted category [{}] and its descendants", categoryId);
        }

        public SearchResult searchDocuments(String query, int page, int size) {
                if (!StringUtils.hasText(query)) {
                        return new SearchResult(List.of(), 0L);
                }

                int safePage = Math.max(page, 0);
                int safeSize = size > 0 ? size : 10;
                Pageable pageable = PageRequest.of(safePage, safeSize);

                NativeQuery nativeQuery = NativeQuery.builder()
                        .withQuery(q -> q.multiMatch(m -> m
                                .query(query)
                                .fields("title^2", "content")))
                        .withPageable(pageable)
                        .build();

                SearchHits<DocumentationDocument> searchHits = elasticsearchOperations.search(nativeQuery, DocumentationDocument.class);
                List<DocumentationDocument> documents = new ArrayList<>();
                for (SearchHit<DocumentationDocument> hit : searchHits) {
                        documents.add(hit.getContent());
                }

                long totalHits = searchHits.getTotalHits();
                long total = totalHits >= 0 ? totalHits : documents.size();
                return new SearchResult(documents, total);
        }

        private String normalizeParentId(String parentId) {
                if (!StringUtils.hasText(parentId)) {
                        return ROOT_CATEGORY_ID;
                }
                String trimmed = parentId.trim();
                return ROOT_CATEGORY_ID.equalsIgnoreCase(trimmed) ? ROOT_CATEGORY_ID : trimmed;
        }

        public record SearchResult(List<DocumentationDocument> documents, long totalHits) {
        }

        public record CategoryNode(CategoryDocument category, List<CategoryNode> children) {
        }
}
