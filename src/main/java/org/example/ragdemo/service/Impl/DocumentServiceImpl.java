package org.example.ragdemo.service.Impl;

import org.example.ragdemo.service.DocumentService;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
public class DocumentServiceImpl implements DocumentService {
    private final VectorStore vectorStore;

    public DocumentServiceImpl(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /** 解析 PDF 字节 -> 分块 -> 向量化 -> 写入 ES，返回分块数量 */
    public int processPdf(byte[] content, String fileName) throws IOException {
        // 1. 字节转成 Spring Resource（保留原始文件名，用于 ES 元数据）
        ByteArrayResource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
        // 2. 解析 PDF：默认每页一个 Document（自动带上 file_name / page_number 元数据）
        PagePdfDocumentReader reader = new PagePdfDocumentReader(resource);
        List<Document> pages = reader.read();

        // 3. 分块：每块约 800 token
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(800)
                .withMinChunkSizeChars(350)
                .withMinChunkLengthToEmbed(5)
                .withMaxNumChunks(10000)
                .withKeepSeparator(true)
                .build();
        List<Document> chunks = splitter.apply(pages);

        // 4. 向量化并写入 ES
        vectorStore.add(chunks);
        return chunks.size();
    }
}
