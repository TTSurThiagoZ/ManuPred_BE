package com.fiec.br.back_end.kipper.features.user.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Resultado da importação de usuários por CSV. As linhas válidas são criadas "
        + "mesmo que outras tenham erro; as com erro são listadas em 'erros'.")
public record ImportacaoUsuariosResponseDTO(
        @Schema(description = "Quantidade de linhas de dados lidas (sem contar o cabeçalho e linhas em branco)")
        int linhasLidas,
        @Schema(description = "Quantidade de usuários criados")
        int criados,
        @Schema(description = "Usuários criados")
        List<UserResponseDTO> usuariosCriados,
        @Schema(description = "Linhas que não foram importadas e o motivo")
        List<ErroLinha> erros
) {
    public record ErroLinha(
            @Schema(description = "Número da linha no arquivo (o cabeçalho é a linha 1)") int linha,
            @Schema(description = "E-mail informado na linha, se houver") String email,
            @Schema(description = "Motivo de a linha não ter sido importada") String motivo
    ) {}
}
