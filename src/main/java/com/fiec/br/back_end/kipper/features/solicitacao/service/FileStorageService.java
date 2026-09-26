package com.fiec.br.back_end.kipper.features.solicitacao.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
public class FileStorageService {

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    private static final List<String> CONTENT_TYPES_IMAGEM = List.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp", "image/bmp"
    );

    private static final List<String> CONTENT_TYPES_CSV = List.of(
            "text/csv", "application/csv", "application/vnd.ms-excel", "text/plain"
    );

    private static final List<String> EXTENSOES_PERMITIDAS = List.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".csv"
    );

    public String salvar(UUID solicitacaoId, MultipartFile arquivo) {
        validarTipoArquivo(arquivo);

        try {
            Path pastaSolicitacao = Path.of(uploadDir, solicitacaoId.toString()).toAbsolutePath().normalize();
            Files.createDirectories(pastaSolicitacao);

            String nomeOriginal = arquivo.getOriginalFilename() != null ? arquivo.getOriginalFilename() : "arquivo";
            // Descarta qualquer caminho embutido no nome enviado pelo cliente (ex: "../../etc/x.jpg"),
            // mantendo só o nome do arquivo em si, para evitar escrever fora da pasta de uploads.
            String nomeOriginalSanitizado = Path.of(nomeOriginal).getFileName().toString();
            String nomeArmazenado = UUID.randomUUID() + "_" + nomeOriginalSanitizado;
            Path destino = pastaSolicitacao.resolve(nomeArmazenado).normalize();

            if (!destino.startsWith(pastaSolicitacao)) {
                throw new IllegalArgumentException("Nome de arquivo inválido: " + nomeOriginal);
            }

            try (InputStream in = arquivo.getInputStream()) {
                Files.copy(in, destino);
            }

            return solicitacaoId + "/" + nomeArmazenado;
        } catch (IOException e) {
            throw new RuntimeException("Falha ao salvar anexo: " + e.getMessage(), e);
        }
    }

    private void validarTipoArquivo(MultipartFile arquivo) {
        String contentType = arquivo.getContentType() != null
                ? arquivo.getContentType().toLowerCase(Locale.ROOT)
                : "";
        String nomeOriginal = arquivo.getOriginalFilename() != null
                ? arquivo.getOriginalFilename().toLowerCase(Locale.ROOT)
                : "";

        boolean contentTypeValido = CONTENT_TYPES_IMAGEM.contains(contentType)
                || CONTENT_TYPES_CSV.contains(contentType);
        boolean extensaoValida = EXTENSOES_PERMITIDAS.stream().anyMatch(nomeOriginal::endsWith);

        if (!contentTypeValido && !extensaoValida) {
            throw new IllegalArgumentException(
                    "Tipo de arquivo não permitido: " + arquivo.getOriginalFilename() +
                            ". Só são aceitos imagens (jpg, jpeg, png, gif, webp, bmp) ou arquivos CSV."
            );
        }
    }
}