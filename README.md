# Tatakai Manager — Backend

API REST + WebSocket para gerenciamento de NPCs de RPG de mesa (Spring Boot 3.3 / Java 21).

## Pré-requisitos

- **JDK 21** (o projeto compila para bytecode 21 e é homologado nessa versão).
- O Maven não precisa estar instalado — use o wrapper `./mvnw`.

> ⚠️ A `java` padrão deste ambiente é um JRE 25 (sem `javac`). Antes de qualquer
> comando Maven, aponte o `JAVA_HOME` para um JDK 21:
>
> ```bash
> export JAVA_HOME=~/.jdks/jbr-21.0.11   # ajuste para o seu caminho de JDK 21
> export PATH="$JAVA_HOME/bin:$PATH"
> ```

## Comandos

```bash
# Rodar todos os testes
./mvnw test

# Rodar uma classe de teste específica
./mvnw test -Dtest=AuthServiceTest

# Subir a aplicação (requer PostgreSQL — ver variáveis abaixo)
./mvnw spring-boot:run
```

## Variáveis de ambiente

| Variável | Padrão | Descrição |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/tatakai` | URL do PostgreSQL |
| `DB_USER` | `postgres` | Usuário do banco |
| `DB_PASS` | `postgres` | Senha do banco |
| `JWT_SECRET` | (dev) | Segredo HMAC (mín. 32 chars) — **defina em produção** |
| `FRONTEND_URL` | `http://localhost:5173` | Origem liberada no CORS |

## Estrutura

```
src/main/java/com/tatakai/manager/
├── config/      — SecurityConfig, CORS, (WebSocket no Sprint 6)
├── controller/  — endpoints REST
├── dto/         — request/response (Bean Validation)
├── entity/      — entidades JPA
├── exception/   — exceções de domínio + handler global
├── repository/  — Spring Data JPA
├── security/    — JwtService, filtro de autenticação
└── service/     — regras de negócio
```

## Status por Sprint

- [x] **Sprint 1** — Autenticação (cadastro/login com JWT) — `AuthServiceTest` ✓
- [ ] Sprint 2 — Campanhas e membros
- [ ] Sprint 3 — NPCs
- [ ] Sprint 4 — TimeSkip
- [ ] Sprint 5 — Agendamento (REST)
- [ ] Sprint 6 — WebSocket (tempo real)
- [ ] Sprint 7 — Log narrativo
- [ ] Sprint 8 — Deploy
