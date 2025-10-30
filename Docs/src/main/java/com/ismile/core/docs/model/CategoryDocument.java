package com.ismile.core.docs.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "docs_categories")
public class CategoryDocument {

        @Id
        private String id;

        @Field(type = FieldType.Text)
        private String name;

        @Field(type = FieldType.Keyword, name = "parent_id")
        private String parentId;
}
