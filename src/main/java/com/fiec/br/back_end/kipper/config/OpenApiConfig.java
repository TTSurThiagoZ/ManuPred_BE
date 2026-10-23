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
                version = "v1.0",
                description = """
                        API REST para gestão de chamados de manutenção predial.
                        
                        **Recursos:**
                        - Autenticação via JWT e Google Sign-In
                        - Ciclo de vida completo de chamados
                        - SLA automático
                        - Notificações push (FCM)
                        - Relatórios e auditoria
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
                // Utilizar "/" faz o Swagger usar a mesma origem (IP/Host e porta) de onde a página foi aberta
                @Server(url = "/", description = "Servidor Atual")
        },
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
        name = "bearerAuth",
        description = "Insira o token JWT obtido em /api/v1/auth/login ou /api/v1/auth/google",
        scheme = "bearer",
        type = SecuritySchemeType.HTTP,
        bearerFormat = "JWT",
        in = SecuritySchemeIn.HEADER
)
public class OpenApiConfig {
}