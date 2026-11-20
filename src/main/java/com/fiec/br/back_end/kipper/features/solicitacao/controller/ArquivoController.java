package com.fiec.br.back_end.kipper.features.solicitacao.controller;

import com.fiec.br.back_end.kipper.exception.RecursoNaoEncontradoException;
import com.fiec.br.back_end.kipper.features.solicitacao.service.FileStorageService;
import com.fiec.br.back_end.kipper.features.solicitacao.service.armazenamento.ArquivoArmazenado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Entrega os anexos em /uploads/{idDoChamado}/{arquivo}, venham eles da pasta local ou do S3.
 * O endereço é o mesmo de antes (campo "url" do AnexoResponseDTO), então o app e o site
 * não precisam mudar nada.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Anexos", description = "Download das imagens e arquivos anexados aos chamados")
public class ArquivoController {

    private static final Pattern NOME_SEGURO = Pattern.compile("[A-Za-z0-9._-]{1,200}");

    private final FileStorageService fileStorageService;

    @Operation(summary = "Baixar anexo",
            description = "Use o valor do campo 'url' que vem nos anexos do chamado. Não precisa de login.")
    @SecurityRequirements // sem cadeado no Swagger: a rota é pública
    @GetMapping("/uploads/{pasta}/{arquivo}")
    public ResponseEntity<InputStreamResource> baixar(@PathVariable String pasta, @PathVariable String arquivo) {
        // Só aceita {UUID}/{nome-simples}: impede caminhos como ../../ para fora da pasta
        if (!ehUuid(pasta) || !NOME_SEGURO.matcher(arquivo).matches() || arquivo.contains("..")) {
            throw new RecursoNaoEncontradoException("Arquivo não encontrado.");
        }

        ArquivoArmazenado anexo = fileStorageService.abrir(pasta + "/" + arquivo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Arquivo não encontrado."));

        ResponseEntity.BodyBuilder resposta = ResponseEntity.ok()
                .contentType(FileStorageService.tipoDoArquivo(arquivo))
                // O nome do arquivo é único (tem um UUID), então o navegador pode guardar em cache
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(arquivo).build().toString());

        if (anexo.tamanho() >= 0) {
            resposta.contentLength(anexo.tamanho());
        }
        return resposta.body(new InputStreamResource(anexo.conteudo()));
    }

    /** Imagens maiores que isso não são enviadas ao agente (o base64 aumenta ~33%). */
    private static final long LIMITE_BYTES_MCP = 5L * 1024 * 1024;

    /**
     * Versão de {@link #baixar} para agentes de IA (MCP). Não é um endpoint HTTP: devolve a
     * imagem como conteúdo de imagem do MCP, para o agente conseguir "ver" a foto do problema.
     */
    @McpTool(name = "ver_anexo",
            description = "Abre um anexo de chamado. Imagens são devolvidas como imagem para você analisar; "
                    + "outros arquivos retornam só o tipo e o tamanho. Pegue o campo 'url' de um anexo "
                    + "(retornado por 'buscar_chamado'), no formato /uploads/{pasta}/{arquivo}.",
            annotations = @McpTool.McpAnnotations(title = "Ver anexo",
                    readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public McpSchema.CallToolResult verAnexo(
            @McpToolParam(description = "Campo 'url' do anexo, ex.: /uploads/0b6c...-uuid/foto.jpg") String url) {
        String caminho = url == null ? "" : url.trim().replaceFirst("^https?://[^/]+", "").replaceFirst("^/?uploads/", "");
        String[] partes = caminho.split("/");
        if (partes.length != 2 || !ehUuid(partes[0]) || !NOME_SEGURO.matcher(partes[1]).matches() || partes[1].contains("..")) {
            return erro("URL de anexo inválida. Use o campo 'url' do anexo, no formato /uploads/{pasta}/{arquivo}.");
        }

        ArquivoArmazenado anexo = fileStorageService.abrir(partes[0] + "/" + partes[1]).orElse(null);
        if (anexo == null) {
            return erro("Arquivo não encontrado.");
        }

        MediaType tipo = FileStorageService.tipoDoArquivo(partes[1]);
        try (InputStream in = anexo.conteudo()) {
            if (!"image".equals(tipo.getType())) {
                return McpSchema.CallToolResult.builder()
                        .addTextContent("Anexo '" + partes[1] + "' do tipo " + tipo + " (" + anexo.tamanho()
                                + " bytes). Só imagens podem ser exibidas aqui.")
                        .build();
            }
            byte[] bytes = in.readNBytes((int) LIMITE_BYTES_MCP + 1);
            if (bytes.length > LIMITE_BYTES_MCP) {
                return erro("Imagem grande demais para enviar ao agente (limite de 5 MB).");
            }
            return McpSchema.CallToolResult.builder()
                    .addContent(new McpSchema.ImageContent(null, Base64.getEncoder().encodeToString(bytes), tipo.toString()))
                    .build();
        } catch (IOException e) {
            return erro("Falha ao ler o anexo: " + e.getMessage());
        }
    }

    private static McpSchema.CallToolResult erro(String mensagem) {
        return McpSchema.CallToolResult.builder().addTextContent(mensagem).isError(true).build();
    }

    private boolean ehUuid(String valor) {
        try {
            UUID.fromString(valor);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
