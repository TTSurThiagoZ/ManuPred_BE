package com.fiec.br.back_end.kipper.features.solicitacao.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Component
public class FileStorageService {

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    public String salvar(UUID solicitacaoId, MultipartFile arquivo) {
        try {
            Path pastaSolicitacao = Path.of(uploadDir, solicitacaoId.toString());
            Files.createDirectories(pastaSolicitacao);

            String nomeOriginal = arquivo.getOriginalFilename() != null ? arquivo.getOriginalFilename() : "arquivo";
            String nomeArmazenado = UUID.randomUUID() + "_" + nomeOriginal;
            Path destino = pastaSolicitacao.resolve(nomeArmazenado);

            try (InputStream in = arquivo.getInputStream()) {
                Files.copy(in, destino);
            }

            return solicitacaoId + "/" + nomeArmazenado;
        } catch (IOException e) {
            throw new RuntimeException("Falha ao salvar anexo: " + e.getMessage(), e);
        }
    }
}
