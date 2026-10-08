package org.example.ragdemo.service.Impl;

import org.example.ragdemo.service.DocumentService;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
public class DocumentServiceImpl implements DocumentService {
    private final VectorStore vectorStore;

    public DocumentServiceImpl(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }
    /** 上传 PDF：解析 -> 分块 -> 向量化 -> 写入 ES，返回分块数量 */
    public int ingest(MultipartFile file) throws IOException {
        // 1. 上传文件转成 Spring Resource（保留原始文件名，用于 ES 元数据）
        ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        };
        // 2. 解析 PDF：默认每页一个 Document（自动带上 file_name / page_number 元数据）
        PagePdfDocumentReader reader = new PagePdfDocumentReader(resource);
        List<Document> pages = reader.read();

        // 3. 分块：每块约 800 token（2.0 用 builder 创建）
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(800)
                .withMinChunkSizeChars(350)
                .withMinChunkLengthToEmbed(5)
                .withMaxNumChunks(10000)
                .withKeepSeparator(true)
                .build();
        List<Document> chunks = splitter.apply(pages);

        // 4. 向量化并写入 ES（会调用百炼 embedding 生成向量）
        vectorStore.add(chunks);
        return chunks.size();
    }
}
