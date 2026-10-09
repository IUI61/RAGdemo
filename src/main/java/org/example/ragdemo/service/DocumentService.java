package org.example.ragdemo.service;

import java.io.IOException;

public interface DocumentService {
    /** 解析 PDF 字节并入库，返回分块数量（由消息消费者异步调用） */
    int processPdf(byte[] content, String fileName) throws IOException;
}
