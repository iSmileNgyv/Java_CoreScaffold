package com.ismile.core.docs.repository;

import com.ismile.core.docs.model.DocumentationDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface DocumentationRepository extends ElasticsearchRepository<DocumentationDocument, String> {

        List<DocumentationDocument> findByCategoryId(String categoryId);
}
