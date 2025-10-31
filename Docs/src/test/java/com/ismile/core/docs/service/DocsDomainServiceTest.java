package com.ismile.core.docs.service;

import com.ismile.core.docs.model.CategoryDocument;
import com.ismile.core.docs.model.DocumentationDocument;
import com.ismile.core.docs.repository.CategoryRepository;
import com.ismile.core.docs.repository.DocumentationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocsDomainServiceTest {

        @Mock
        private CategoryRepository categoryRepository;

        @Mock
        private DocumentationRepository documentationRepository;

        @Mock
        private ElasticsearchOperations elasticsearchOperations;

        private DocsDomainService docsDomainService;

        @BeforeEach
        void setUp() {
                docsDomainService = new DocsDomainService(categoryRepository, documentationRepository, elasticsearchOperations);
        }

        @Test
        void createCategoryAssignsRootWhenParentMissing() {
                when(categoryRepository.save(argThat(withParent(DocsDomainService.ROOT_CATEGORY_ID))))
                        .thenAnswer(invocation -> invocation.getArgument(0));

                CategoryDocument result = docsDomainService.createCategory("  Getting Started  ", null);

                assertThat(result.getName()).isEqualTo("Getting Started");
                assertThat(result.getParentId()).isEqualTo(DocsDomainService.ROOT_CATEGORY_ID);
        }

        @Test
        void createCategoryRejectsEmptyName() {
                assertThatThrownBy(() -> docsDomainService.createCategory("   ", null))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("must not be empty");

                verify(categoryRepository, never()).save(org.mockito.ArgumentMatchers.any());
        }

        @Test
        void createCategoryRequiresExistingParent() {
                when(categoryRepository.findById("parent-1")).thenReturn(Optional.empty());

                assertThatThrownBy(() -> docsDomainService.createCategory("Child", "parent-1"))
                        .isInstanceOf(NoSuchElementException.class)
                        .hasMessageContaining("Parent category not found");
        }

        @Test
        void createDocumentationPersistsWithNormalizedCategory() {
                CategoryDocument parent = CategoryDocument.builder()
                        .id("cat-1")
                        .name("Docs")
                        .parentId(DocsDomainService.ROOT_CATEGORY_ID)
                        .build();
                when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(parent));
                when(documentationRepository.save(argThat(doc -> "cat-1".equals(doc.getCategoryId()))))
                        .thenAnswer(invocation -> invocation.getArgument(0));

                DocumentationDocument document = docsDomainService.createDocumentation("  Intro  ", "content", "cat-1");

                assertThat(document.getTitle()).isEqualTo("Intro");
                assertThat(document.getContent()).isEqualTo("content");
                assertThat(document.getCategoryId()).isEqualTo("cat-1");
        }

        @Test
        void createDocumentationRequiresExistingCategory() {
                when(categoryRepository.findById("missing")).thenReturn(Optional.empty());

                assertThatThrownBy(() -> docsDomainService.createDocumentation("Intro", "content", "missing"))
                        .isInstanceOf(NoSuchElementException.class)
                        .hasMessageContaining("Category not found");
        }

        @Test
        void searchDocumentsShortCircuitsOnBlankQuery() {
                DocsDomainService.SearchResult result = docsDomainService.searchDocuments("   ", 2, 5);

                assertThat(result.documents()).isEmpty();
                assertThat(result.totalHits()).isZero();
                verifyNoInteractions(elasticsearchOperations);
        }

        @Test
        void getCategoryTreeBuildsNestedStructure() {
                CategoryDocument core = CategoryDocument.builder()
                        .id("core")
                        .name("Core")
                        .parentId(DocsDomainService.ROOT_CATEGORY_ID)
                        .build();
                CategoryDocument misc = CategoryDocument.builder()
                        .id("misc")
                        .name("Misc")
                        .parentId(DocsDomainService.ROOT_CATEGORY_ID)
                        .build();
                CategoryDocument auth = CategoryDocument.builder()
                        .id("auth")
                        .name("Auth")
                        .parentId("core")
                        .build();
                CategoryDocument otp = CategoryDocument.builder()
                        .id("otp")
                        .name("OTP")
                        .parentId("core")
                        .build();

                when(categoryRepository.findByParentId(DocsDomainService.ROOT_CATEGORY_ID)).thenReturn(List.of(core, misc));
                when(categoryRepository.findByParentId("core")).thenReturn(List.of(auth, otp));
                when(categoryRepository.findByParentId("misc")).thenReturn(List.of());
                when(categoryRepository.findByParentId("auth")).thenReturn(List.of());
                when(categoryRepository.findByParentId("otp")).thenReturn(List.of());

                List<DocsDomainService.CategoryNode> tree = docsDomainService.getCategoryTree();

                assertThat(tree).hasSize(2);
                DocsDomainService.CategoryNode coreNode = tree.get(0);
                assertThat(coreNode.category()).isEqualTo(core);
                assertThat(coreNode.children()).hasSize(2)
                        .extracting(DocsDomainService.CategoryNode::category)
                        .containsExactly(auth, otp);
                DocsDomainService.CategoryNode miscNode = tree.get(1);
                assertThat(miscNode.category()).isEqualTo(misc);
                assertThat(miscNode.children()).isEmpty();
        }

        private ArgumentMatcher<CategoryDocument> withParent(String parentId) {
                return category -> parentId.equals(category.getParentId());
        }
}
