package com.java.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RedmineIssueDto {
    private int id;
    private String subject;
    private String statusName;
    private String url;
}
