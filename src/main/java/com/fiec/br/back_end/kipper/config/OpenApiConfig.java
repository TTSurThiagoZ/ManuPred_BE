package com.fiec.br.back_end.kipper.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "ManuPred API",
                version = "v1.1",
                description = """
                        API REST para gestão de chamados de manutenção predial: alguém abre uma solicitação,
                        um técnico recebe, resolve, e quem abriu valida.

                        ### Como testar por aqui
                        1. Abra **Autenticação → POST /api/v1/auth/login**, clique em **Try it out** e em **Execute**
                           (o exemplo do admin já vem preenchido).
                        2. Copie o valor de `token` da resposta.
                        3. Clique em **Authorize** (cadeado, no topo), cole o token **sem** a palavra `Bearer` e confirme.
                        4. O token fica salvo mesmo se você recarregar a página. Ele vale 30 minutos; depois disso,
                           as chamadas voltam **401** e é só fazer login de novo.

                        ### Busca
                        O campo **Buscar** filtra por rota, nome, descrição e método, sem diferenciar acento nem
                        maiúscula. Exemplos: `comentario`, `ia`, `patch`, `admin tipos`.

                        ### Perfis de usuário
                        | Perfil | O que pode fazer |
                        |---|---|
                        | `USER_ROLE` | Abrir chamados; ver e comentar os próprios; validar ou rejeitar a conclusão; excluir o próprio chamado enquanto estiver `ABERTO`; organizar a descrição com IA. |
                        | `FUNCIONARIO_ROLE` | Tudo do usuário + ver todos os chamados; mudar status, concluir e gerenciar ajudantes **nos chamados em que é técnico ou ajudante**; resumo com IA. |
                        | `SUPERVISOR_ROLE` | Tudo do funcionário **em qualquer chamado** + atribuir técnico, mudar prioridade, marcar urgência, rejeitar e busca em linguagem natural com IA. |
                        | `ADMIN_ROLE` | Tudo + tipos, setores, locais, usuários e perfis, relatórios, auditoria, excluir qualquer chamado e reabrir chamados encerrados. |
                        | `SUPER_ADMIN_ROLE` | Tudo do admin + importar usuários por CSV e conceder/remover a role `SUPER_ADMIN_ROLE`. |

                        Na primeira inicialização, se não existir nenhum admin, o sistema cria `admin@manupred.com`
                        com a senha da variável `ADMIN_PASSWORD`.

                        ### Ciclo de vida do chamado
                        `ABERTO` → `EM_ANDAMENTO` (ao atribuir um técnico) → `AGUARDANDO_ASSISTENCIA_EXTERNA` (opcional)
                        → `CONCLUIDO` (técnico terminou) → `FINALIZADA` (solicitante validou).

                        Também pode terminar em `REJEITADA` (com motivo) ou `CANCELADO`. Chamados encerrados
                        só podem ser reabertos por um ADMIN.

                        ### Recursos
                        - **Login** com e-mail e senha, Google Sign-In e Firebase, todos devolvendo um token JWT
                        - **Anexos**: imagens (jpg, png, gif, webp, bmp) e CSV, até 10MB por arquivo e 50MB por envio,
                          servidos em `/uploads/...`
                        - **Comentários** e **histórico** de tudo que aconteceu no chamado
                        - **Ajudantes**: outros funcionários trabalhando junto com o técnico
                        - **SLA automático** por tipo de chamado: quando estoura, o chamado vira urgente e o técnico é avisado
                        - **Notificações push** (Firebase Cloud Messaging) para solicitante e técnico
                        - **Inteligência Artificial** (Gemini): busca de chamados em português comum, organização da
                          descrição do problema e resumo do histórico do chamado
                        - **Relatórios** (total, tempo médio de resolução, tipo mais frequente, rejeições) e **auditoria**
                          das ações administrativas

                        ### Erros
                        Os erros voltam como `{"erro": "mensagem"}`. Erros de validação de campos voltam como
                        `{"campo": "mensagem"}`.

                        | Código | Quando acontece |
                        |---|---|
                        | 400 | Dados inválidos ou regra de negócio (ex.: finalizar um chamado que não está `CONCLUIDO`) |
                        | 401 | Sem token, ou token vencido |
                        | 403 | O perfil do usuário não tem permissão para essa ação |
                        | 404 | Chamado, usuário, tipo, setor ou local não encontrado |
                        | 429 | Limite de uso da IA atingido |
                        | 503 | IA indisponível no momento |
                        """,
                contact = @Contact(
                        name = "Equipe ManuPred",
                        email = "contato@manupred.com"
                ),
                license = @License(
                        name = "MIT",
                        url = "https://opensource.org/licenses/MIT"
                )
        ),
        servers = {
                // Permite configurar a URL base (ex: /kipper no API Gateway) ou manter "/" por padrão
                @Server(url = "${app.swagger.server-url:/}", description = "Servidor atual")
        },
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
        name = "bearerAuth",
        description = "Cole aqui só o token devolvido por /api/v1/auth/login, /api/v1/auth/google ou "
                + "/api/v1/users/auth/firebase (sem a palavra Bearer).",
        scheme = "bearer",
        type = SecuritySchemeType.HTTP,
        bearerFormat = "JWT",
        in = SecuritySchemeIn.HEADER
)
public class OpenApiConfig {
}
