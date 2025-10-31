package com.ismile.core.docs.service;

import com.ismile.core.docs.model.CategoryDocument;
import com.ismile.core.docs.model.DocumentationDocument;
import com.ismile.core.docs.service.DocsDomainService.SearchResult;
import docs.Category;
import docs.CategoryContentsResponse;
import docs.CategoryResponse;
import docs.CategoryTreeNode;
import docs.CategoryTreeResponse;
import docs.CreateCategoryRequest;
import docs.CreateDocumentationRequest;
import docs.DeleteCategoryRequest;
import docs.DeleteDocumentationRequest;
import docs.DeleteResponse;
import docs.DocsServiceGrpc;
import docs.Documentation;
import docs.DocumentationResponse;
import docs.GetDocumentationRequest;
import docs.ListCategoriesRequest;
import docs.ListCategoryContentsRequest;
import docs.SearchDocumentsRequest;
import docs.SearchDocumentsResponse;
import docs.UpdateDocumentationRequest;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.server.service.GrpcService;

import java.util.List;
import java.util.NoSuchElementException;

@GrpcService
@Slf4j
@RequiredArgsConstructor
public class DocsGrpcService extends DocsServiceGrpc.DocsServiceImplBase {

        private final DocsDomainService domainService;

        @Override
        public void createCategory(CreateCategoryRequest request, StreamObserver<CategoryResponse> responseObserver) {
                try {
                        CategoryDocument category = domainService.createCategory(request.getName(), request.getParentId());
                        CategoryResponse response = CategoryResponse.newBuilder()
                                .setCategory(toProto(category))
                                .build();
                        responseObserver.onNext(response);
                        responseObserver.onCompleted();
                } catch (IllegalArgumentException e) {
                        log.warn("Failed to create category: {}", e.getMessage());
                        responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
                } catch (NoSuchElementException e) {
                        log.warn("Failed to create category: {}", e.getMessage());
                        responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
                } catch (Exception e) {
                        log.error("Unexpected error while creating category", e);
                        responseObserver.onError(Status.INTERNAL.withDescription("Unable to create category").asRuntimeException());
                }
        }

        @Override
        public void createDocumentation(CreateDocumentationRequest request, StreamObserver<DocumentationResponse> responseObserver) {
                try {
                        DocumentationDocument document = domainService.createDocumentation(
                                request.getTitle(), request.getContent(), request.getCategoryId());
                        DocumentationResponse response = DocumentationResponse.newBuilder()
                                .setDocument(toProto(document))
                                .build();
                        responseObserver.onNext(response);
                        responseObserver.onCompleted();
                } catch (IllegalArgumentException e) {
                        log.warn("Failed to create documentation: {}", e.getMessage());
                        responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
                } catch (NoSuchElementException e) {
                        log.warn("Failed to create documentation: {}", e.getMessage());
                        responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
                } catch (Exception e) {
                        log.error("Unexpected error while creating documentation", e);
                        responseObserver.onError(Status.INTERNAL.withDescription("Unable to create documentation").asRuntimeException());
                }
        }

        @Override
        public void listCategoryContents(ListCategoryContentsRequest request, StreamObserver<CategoryContentsResponse> responseObserver) {
                try {
                        String categoryId = request.getCategoryId();
                        List<CategoryDocument> categories = domainService.listSubCategories(categoryId);
                        List<DocumentationDocument> documents = domainService.listDocumentsByCategory(categoryId);

                        CategoryContentsResponse.Builder builder = CategoryContentsResponse.newBuilder();
                        categories.stream().map(this::toProto).forEach(builder::addCategories);
                        documents.stream().map(this::toProto).forEach(builder::addDocuments);

                        responseObserver.onNext(builder.build());
                        responseObserver.onCompleted();
                } catch (Exception e) {
                        log.error("Failed to list contents for category [{}]", request.getCategoryId(), e);
                        responseObserver.onError(Status.INTERNAL.withDescription("Unable to list category contents").asRuntimeException());
                }
        }

        @Override
        public void listCategories(ListCategoriesRequest request, StreamObserver<CategoryTreeResponse> responseObserver) {
                try {
                        List<DocsDomainService.CategoryNode> tree = domainService.getCategoryTree();
                        CategoryTreeResponse.Builder builder = CategoryTreeResponse.newBuilder();
                        tree.stream().map(this::toProto).forEach(builder::addCategories);
                        responseObserver.onNext(builder.build());
                        responseObserver.onCompleted();
                } catch (Exception e) {
                        log.error("Failed to list categories", e);
                        responseObserver.onError(Status.INTERNAL.withDescription("Unable to list categories").asRuntimeException());
                }
        }

        @Override
        public void getDocumentation(GetDocumentationRequest request, StreamObserver<DocumentationResponse> responseObserver) {
                try {
                        DocumentationDocument document = domainService.getDocumentation(request.getDocumentId());
                        DocumentationResponse response = DocumentationResponse.newBuilder()
                                .setDocument(toProto(document))
                                .build();
                        responseObserver.onNext(response);
                        responseObserver.onCompleted();
                } catch (NoSuchElementException e) {
                        log.warn("Document [{}] not found", request.getDocumentId());
                        responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
                } catch (Exception e) {
                        log.error("Failed to fetch document [{}]", request.getDocumentId(), e);
                        responseObserver.onError(Status.INTERNAL.withDescription("Unable to load documentation").asRuntimeException());
                }
        }

        @Override
        public void updateDocumentation(UpdateDocumentationRequest request, StreamObserver<DocumentationResponse> responseObserver) {
                try {
                        DocumentationDocument document = domainService.updateDocumentation(
                                request.getDocumentId(), request.getTitle(), request.getContent());
                        DocumentationResponse response = DocumentationResponse.newBuilder()
                                .setDocument(toProto(document))
                                .build();
                        responseObserver.onNext(response);
                        responseObserver.onCompleted();
                } catch (IllegalArgumentException e) {
                        log.warn("Invalid request for updating documentation: {}", e.getMessage());
                        responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
                } catch (NoSuchElementException e) {
                        log.warn("Document [{}] not found for update", request.getDocumentId());
                        responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
                } catch (Exception e) {
                        log.error("Failed to update document [{}]", request.getDocumentId(), e);
                        responseObserver.onError(Status.INTERNAL.withDescription("Unable to update documentation").asRuntimeException());
                }
        }

        @Override
        public void deleteCategory(DeleteCategoryRequest request, StreamObserver<DeleteResponse> responseObserver) {
                try {
                        domainService.deleteCategoryRecursively(request.getCategoryId());
                        DeleteResponse response = DeleteResponse.newBuilder()
                                .setSuccess(true)
                                .setMessage("Category deleted")
                                .build();
                        responseObserver.onNext(response);
                        responseObserver.onCompleted();
                } catch (NoSuchElementException e) {
                        log.warn("Category [{}] not found for deletion", request.getCategoryId());
                        responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
                } catch (Exception e) {
                        log.error("Failed to delete category [{}]", request.getCategoryId(), e);
                        responseObserver.onError(Status.INTERNAL.withDescription("Unable to delete category").asRuntimeException());
                }
        }

        @Override
        public void deleteDocumentation(DeleteDocumentationRequest request, StreamObserver<DeleteResponse> responseObserver) {
                try {
                        domainService.deleteDocumentation(request.getDocumentId());
                        DeleteResponse response = DeleteResponse.newBuilder()
                                .setSuccess(true)
                                .setMessage("Document deleted")
                                .build();
                        responseObserver.onNext(response);
                        responseObserver.onCompleted();
                } catch (NoSuchElementException e) {
                        log.warn("Document [{}] not found for deletion", request.getDocumentId());
                        responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
                } catch (Exception e) {
                        log.error("Failed to delete document [{}]", request.getDocumentId(), e);
                        responseObserver.onError(Status.INTERNAL.withDescription("Unable to delete document").asRuntimeException());
                }
        }

        @Override
        public void searchDocuments(SearchDocumentsRequest request, StreamObserver<SearchDocumentsResponse> responseObserver) {
                try {
                        SearchResult result = domainService.searchDocuments(request.getQuery(), request.getPage(), request.getSize());
                        SearchDocumentsResponse.Builder builder = SearchDocumentsResponse.newBuilder()
                                .setTotalHits(result.totalHits());
                        result.documents().stream().map(this::toProto).forEach(builder::addDocuments);
                        responseObserver.onNext(builder.build());
                        responseObserver.onCompleted();
                } catch (Exception e) {
                        log.error("Failed to search documents with query [{}]", request.getQuery(), e);
                        responseObserver.onError(Status.INTERNAL.withDescription("Unable to search documents").asRuntimeException());
                }
        }

        private Category toProto(CategoryDocument categoryDocument) {
                Category.Builder builder = Category.newBuilder()
                        .setId(defaultString(categoryDocument.getId()))
                        .setName(defaultString(categoryDocument.getName()));
                if (categoryDocument.getParentId() != null) {
                        builder.setParentId(categoryDocument.getParentId());
                }
                return builder.build();
        }

        private Documentation toProto(DocumentationDocument document) {
                Documentation.Builder builder = Documentation.newBuilder()
                        .setId(defaultString(document.getId()))
                        .setTitle(defaultString(document.getTitle()))
                        .setContent(defaultString(document.getContent()));
                if (document.getCategoryId() != null) {
                        builder.setCategoryId(document.getCategoryId());
                }
                return builder.build();
        }

        private String defaultString(String value) {
                return value == null ? "" : value;
        }

        private CategoryTreeNode toProto(DocsDomainService.CategoryNode node) {
                CategoryTreeNode.Builder builder = CategoryTreeNode.newBuilder()
                        .setCategory(toProto(node.category()));
                node.children().stream()
                        .map(this::toProto)
                        .forEach(builder::addChildren);
                return builder.build();
        }
}
