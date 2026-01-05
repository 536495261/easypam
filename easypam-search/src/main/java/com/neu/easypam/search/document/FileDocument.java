package com.neu.easypam.search.document;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;

/**
 * ES 文件文档
 */
@Data
@Document(indexName = "easypam_file")
@JsonIgnoreProperties(ignoreUnknown = true)
public class FileDocument {
    
    @Id
    private Long id;
    
    @Field(type = FieldType.Long)
    private Long userId;
    
    @Field(type = FieldType.Long)
    private Long parentId;
    
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String fileName;
    
    @Field(type = FieldType.Keyword)
    private String fileType;
    
    @Field(type = FieldType.Keyword)
    private String contentType;
    
    @Field(type = FieldType.Long)
    private Long fileSize;
    
    @Field(type = FieldType.Integer)
    private Integer isFolder;
    
    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    
    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
