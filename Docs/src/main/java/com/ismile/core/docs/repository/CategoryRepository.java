package com.ismile.core.docs.repository;

import com.ismile.core.docs.model.CategoryDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface CategoryRepository extends ElasticsearchRepository<CategoryDocument, String> {

        List<CategoryDocument> findByParentId(String parentId);
}
