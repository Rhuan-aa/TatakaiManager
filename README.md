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
- [x] **Sprint 2** — Campanhas e membros — `CampaignServiceTest` ✓
- [x] **Sprint 3** — NPCs (acervo, associação, visibilidade) — `NpcServiceTest` ✓
- [x] **Sprint 4** — TimeSkip (criação, encerramento, histórico) — `TimeSkipServiceTest` ✓
- [x] **Sprint 5** — Agendamento REST (reserva de slot, cancelamento, conflito) — `BookingServiceTest` ✓
- [x] **Sprint 6** — WebSocket (broadcast em tempo real + auth no CONNECT) — `SlotEventPublisherTest`, `StompAuthChannelInterceptorTest` ✓
- [x] **Sprint 7** — Log narrativo (jogador + Mestre, sanitização XSS) — `LogServiceTest`, `TextSanitizerTest` ✓
- [x] **Sprint 8** — Docker + deploy (Dockerfile, config por env, boot validado contra Postgres) ✓
