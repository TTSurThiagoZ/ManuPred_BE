package com.fiec.br.back_end.kipper.features.user.service.impl;

import com.fiec.br.back_end.kipper.exception.RecursoNaoEncontradoException;
import com.fiec.br.back_end.kipper.config.JwtUtil;
import com.fiec.br.back_end.kipper.features.auth.models.dto.TokenResponseDTO;
import com.fiec.br.back_end.kipper.features.user.model.dto.CreateUserRequestDTO;
import com.fiec.br.back_end.kipper.features.user.model.dto.ImportacaoUsuariosResponseDTO;
import com.fiec.br.back_end.kipper.features.user.model.dto.UserResponseDTO;
import com.fiec.br.back_end.kipper.features.user.model.entities.UserRole;
import com.fiec.br.back_end.kipper.features.user.model.entities.UserStatus;
import com.fiec.br.back_end.kipper.features.user.model.entities.UserType;
import com.fiec.br.back_end.kipper.features.user.model.entities.Users;
import com.fiec.br.back_end.kipper.features.user.repositories.UserRepository;
import com.fiec.br.back_end.kipper.features.user.service.UserService;
import com.fiec.br.back_end.kipper.model.service.AuditService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.fiec.br.back_end.kipper.features.solicitacao.repositories.SolicitacaoRepository;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final SolicitacaoRepository solicitacaoRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuditService auditService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado com E-mail: " + username));
    }

    @Override
    public UserResponseDTO createUser(CreateUserRequestDTO dto) {
        if (userRepository.existsByEmail(dto.email())) {
            throw new IllegalArgumentException("E-mail já cadastrado no sistema.");
        }
        Users user = Users.builder()
                .name(dto.name())
                .email(dto.email())
                .password(passwordEncoder.encode(dto.password()))
                .firebaseUid(dto.firebaseUid())
                .build();
        return UserResponseDTO.fromEntity(userRepository.save(user));
    }

    @Override
    public UserResponseDTO findById(UUID id) {
        return UserResponseDTO.fromEntity(userRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado com ID: " + id)));
    }

    @Override
    public UserResponseDTO findByEmail(String email) {
        return UserResponseDTO.fromEntity(userRepository.findByEmail(email)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado com E-mail: " + email)));
    }

    @Override
    public List<UserResponseDTO> findAll() {
        return userRepository.findAll().stream().map(UserResponseDTO::fromEntity).toList();
    }

    @Override
    public void deleteUser(UUID id) {
        Users alvo = userRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado para deleção."));

        Users autor = getUsuarioLogado();
        if (autor != null && autor.getId().equals(id)) {
            throw new IllegalArgumentException("Você não pode deletar a própria conta.");
        }
        if (alvo.getRole() == UserRole.SUPER_ADMIN_ROLE && !ehSuperAdmin(autor)) {
            throw new AccessDeniedException("Apenas um SUPER_ADMIN pode deletar outro SUPER_ADMIN.");
        }

        if (solicitacaoRepository.existsByUsuarioSolicitanteAtivo(id)
                || solicitacaoRepository.existsByTecnicoResponsavelAtivo(id)) {
            throw new IllegalArgumentException("Não é possível deletar o usuário: ele possui chamados em andamento como solicitante ou técnico responsável.");
        }

        userRepository.deleteById(id);
        auditService.registrar("DELETAR_USUARIO",
                "Deletou usuário: " + alvo.getName() + " (" + alvo.getEmail() + ")", autor);
    }

    @Override
    public TokenResponseDTO verifyAndAuthenticateFirebaseToken(String firebaseToken) {
        FirebaseToken decodedToken;
        try {
            decodedToken = FirebaseAuth.getInstance().verifyIdToken(firebaseToken);
        } catch (Exception e) {
            log.warn("Falha na verificação do token Firebase: {}", e.getMessage());
            throw new BadCredentialsException("Token do Firebase inválido ou expirado.");
        }

        String uid = decodedToken.getUid();
        String email = decodedToken.getEmail();
        String name = decodedToken.getName();

        // O e-mail é o "username" do sistema (vai no subject do JWT). Login por telefone ou
        // anônimo no Firebase não traz e-mail e criaria um usuário que não consegue autenticar.
        if (email == null || email.isBlank()) {
            throw new BadCredentialsException("A conta do Firebase não possui e-mail.");
        }

        Users user = userRepository.findByFirebaseUid(uid)
                .orElseGet(() -> userRepository.findByEmail(email)
                        .map(existingUser -> {
                            existingUser.setFirebaseUid(uid);
                            return userRepository.save(existingUser);
                        })
                        .orElseGet(() -> userRepository.save(Users.builder()
                                .name(name != null ? name : "Usuário Firebase")
                                .email(email)
                                // Senha aleatória (igual ao login Google): nunca deixar senha vazia
                                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                                .firebaseUid(uid)
                                .build())));

        return new TokenResponseDTO(jwtUtil.generateToken(user));
    }

    @Override
    public UserResponseDTO atualizarRole(UUID id, UserRole role) {
        Users user = userRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado com ID: " + id));
        UserRole antiga = user.getRole();
        // Sem isto um ADMIN poderia se promover (ou promover outro) a SUPER_ADMIN
        if ((role == UserRole.SUPER_ADMIN_ROLE || antiga == UserRole.SUPER_ADMIN_ROLE)
                && !ehSuperAdmin(getUsuarioLogado())) {
            throw new AccessDeniedException("Apenas um SUPER_ADMIN pode conceder ou remover a role SUPER_ADMIN_ROLE.");
        }
        user.setRole(role);
        Users salvo = userRepository.save(user);

        auditService.registrar("ALTERAR_ROLE",
                "Alterou role de " + salvo.getName() + ": " + antiga + " → " + role,
                getUsuarioLogado());

        return UserResponseDTO.fromEntity(salvo);
    }

    @Override
    public void atualizarFcmToken(String email, String fcmToken) {
        // Um aparelho só pode estar ligado a um usuário: tira o token de quem usava antes
        List<Users> usuariosComToken = userRepository.findAllByFcmToken(fcmToken);
        for (Users u : usuariosComToken) {
            if (!u.getEmail().equals(email)) {
                u.setFcmToken(null);
                userRepository.save(u);
            }
        }

        Users user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
        user.setFcmToken(fcmToken);
        userRepository.save(user);
    }

    // ============ IMPORTAÇÃO POR CSV ============

    /** O hash BCrypt leva ~0,1 s por senha; acima disso a requisição demoraria demais. */
    static final int MAX_LINHAS_CSV = 1000;
    private static final int TAMANHO_MINIMO_SENHA = 6;
    private static final Pattern EMAIL_VALIDO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /** Nome da coluna no CSV (sem acento, minúsculo) → campo do usuário. */
    private static final Map<String, String> COLUNAS_CSV = Map.ofEntries(
            Map.entry("email", "email"), Map.entry("e-mail", "email"),
            Map.entry("password", "password"), Map.entry("senha", "password"),
            Map.entry("status", "status"),
            Map.entry("rm", "rm"),
            Map.entry("setor", "setor"),
            Map.entry("usertype", "userType"), Map.entry("user_type", "userType"), Map.entry("tipo", "userType"),
            Map.entry("name", "name"), Map.entry("nome", "name"));

    @Override
    public ImportacaoUsuariosResponseDTO importarUsuariosCsv(byte[] conteudoCsv) {
        List<LeitorCsv.Linha> linhas = LeitorCsv.ler(LeitorCsv.decodificar(conteudoCsv));
        if (linhas.isEmpty()) {
            throw new IllegalArgumentException("O arquivo CSV está vazio.");
        }

        List<String> cabecalho = linhas.getFirst().campos();
        Map<String, Integer> indice = lerCabecalho(cabecalho);
        List<LeitorCsv.Linha> dados = linhas.subList(1, linhas.size());
        if (dados.size() > MAX_LINHAS_CSV) {
            throw new IllegalArgumentException("O CSV tem " + dados.size() + " linhas de dados; o máximo por arquivo é "
                    + MAX_LINHAS_CSV + ". Divida em arquivos menores.");
        }

        List<ImportacaoUsuariosResponseDTO.ErroLinha> erros = new ArrayList<>();
        List<Users> novos = new ArrayList<>();
        List<String> senhas = new ArrayList<>();
        Set<String> emailsNoArquivo = new HashSet<>();

        for (LeitorCsv.Linha linha : dados) {
            String email = valor(linha, indice, "email");
            try {
                if (linha.campos().size() > cabecalho.size()) {
                    throw new IllegalArgumentException("A linha tem mais colunas que o cabeçalho.");
                }
                if (email == null) throw new IllegalArgumentException("E-mail é obrigatório.");
                if (!EMAIL_VALIDO.matcher(email).matches()) throw new IllegalArgumentException("E-mail inválido.");
                if (!emailsNoArquivo.add(email.toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException("E-mail repetido no arquivo.");
                }
                if (userRepository.existsByEmail(email)) {
                    throw new IllegalArgumentException("E-mail já cadastrado no sistema.");
                }

                String senha = valor(linha, indice, "password");
                if (senha == null || senha.length() < TAMANHO_MINIMO_SENHA) {
                    throw new IllegalArgumentException("A senha é obrigatória e deve ter no mínimo "
                            + TAMANHO_MINIMO_SENHA + " caracteres.");
                }

                String status = valor(linha, indice, "status");
                String userType = valor(linha, indice, "userType");
                String nome = valor(linha, indice, "name");

                novos.add(Users.builder()
                        // O nome não é obrigatório no CSV; sem ele, usa a parte do e-mail antes do @
                        .name(nome != null ? nome : email.substring(0, email.indexOf('@')))
                        .email(email)
                        .status(status != null
                                ? converterEnum(UserStatus.class, status, "status")
                                : UserStatus.NOT_CONFIRMED)
                        .rm(valor(linha, indice, "rm"))
                        .setor(valor(linha, indice, "setor"))
                        .userType(userType != null ? converterEnum(UserType.class, userType, "userType") : null)
                        // Importação nunca dá permissão extra: todos entram como USER_ROLE
                        .role(UserRole.USER_ROLE)
                        .build());
                senhas.add(senha);
            } catch (IllegalArgumentException e) {
                erros.add(new ImportacaoUsuariosResponseDTO.ErroLinha(linha.numero(), email, e.getMessage()));
            }
        }

        // O hash das senhas é a parte lenta: faz em paralelo
        IntStream.range(0, novos.size()).parallel()
                .forEach(i -> novos.get(i).setPassword(passwordEncoder.encode(senhas.get(i))));

        List<UserResponseDTO> criados = userRepository.saveAll(novos).stream()
                .map(UserResponseDTO::fromEntity).toList();

        if (!criados.isEmpty()) {
            auditService.registrar("IMPORTAR_USUARIOS_CSV",
                    "Importou " + criados.size() + " usuário(s) por CSV", getUsuarioLogado());
        }
        return new ImportacaoUsuariosResponseDTO(dados.size(), criados.size(), criados, erros);
    }

    /** Valida o cabeçalho e devolve campo → posição da coluna. */
    private static Map<String, Integer> lerCabecalho(List<String> cabecalho) {
        Map<String, Integer> indice = new HashMap<>();
        for (int i = 0; i < cabecalho.size(); i++) {
            String original = cabecalho.get(i).trim();
            String campo = COLUNAS_CSV.get(semAcento(original).toLowerCase(Locale.ROOT));
            if (campo == null) {
                throw new IllegalArgumentException("Coluna desconhecida no cabeçalho: '" + original
                        + "'. Colunas aceitas: email, password, status, rm, setor, userType e nome.");
            }
            if (indice.put(campo, i) != null) {
                throw new IllegalArgumentException("Coluna repetida no cabeçalho: '" + original + "'.");
            }
        }
        if (!indice.containsKey("email") || !indice.containsKey("password")) {
            throw new IllegalArgumentException("O cabeçalho do CSV precisa ter as colunas 'email' e 'password'.");
        }
        return indice;
    }

    /** Valor da coluna na linha, sem espaços nas pontas; null se não houver ou estiver vazio. */
    private static String valor(LeitorCsv.Linha linha, Map<String, Integer> indice, String campo) {
        Integer i = indice.get(campo);
        if (i == null || i >= linha.campos().size()) return null;
        String v = linha.campos().get(i).trim();
        return v.isEmpty() ? null : v;
    }

    private static <E extends Enum<E>> E converterEnum(Class<E> tipo, String valor, String campo) {
        String normalizado = semAcento(valor).trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        for (E constante : tipo.getEnumConstants()) {
            if (constante.name().equals(normalizado)) return constante;
        }
        throw new IllegalArgumentException("Valor inválido para '" + campo + "': '" + valor + "'. Aceitos: "
                + Arrays.toString(tipo.getEnumConstants()) + ".");
    }

    private static String semAcento(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private static boolean ehSuperAdmin(Users u) {
        return u != null && u.getRole() == UserRole.SUPER_ADMIN_ROLE;
    }

    private Users getUsuarioLogado() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof Users u ? u : null;
    }
}
