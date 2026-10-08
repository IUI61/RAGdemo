package org.example.ragdemo.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface DocumentService {
    int ingest(MultipartFile file) throws IOException;
}
