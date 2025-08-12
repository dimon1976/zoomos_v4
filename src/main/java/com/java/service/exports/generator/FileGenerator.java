package com.java.service.exports.generator;

import com.java.exception.FileOperationException;
import com.java.model.entity.ExportTemplate;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public interface FileGenerator {
    Path generate(Stream<Map<String, Object>> data,
                  ExportTemplate template,
                  String fileName) throws FileOperationException;

    boolean supports(String format);
}