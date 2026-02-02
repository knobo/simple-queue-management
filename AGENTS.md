# AGENTS.md — Simple Queue

Instruksjoner for AI-agenter som jobber med dette prosjektet.

---

## ⚠️ Java 21 Toolchain — VIKTIG!

Prosjektet krever **Java 21**. Gradle bruker toolchain og vil IKKE fungere med Java 17.

### Før du kjører Gradle-kommandoer:

```bash
# Sett JAVA_HOME til Java 21
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64

# Verifiser
java -version  # Skal vise "21.x.x"
```

### Riktig måte å kjøre tester:

```bash
cd /home/knobo/prog/kilo/simple-queue

# Med eksplisitt JAVA_HOME
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew check

# Eller clean + build
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew clean build
```

### Hvis du får toolchain-feil:

```
Could not resolve all files for configuration ':infrastructure:kotlinCompilerClasspath'
```

**Løsning:** Du bruker feil Java-versjon. Sett `JAVA_HOME` som vist over.

---

## 🏗️ Prosjektstruktur

```
simple-queue/
├── domain/          # Domain models, ports (interfaces)
├── application/     # Use cases, services
├── infrastructure/  # Controllers, repositories, config
│   └── src/
│       ├── main/
│       │   ├── kotlin/    # Kotlin kildekode
│       │   └── resources/
│       │       ├── templates/     # Thymeleaf HTML
│       │       ├── messages*.properties  # i18n
│       │       └── application.yml
│       └── test/          # Tester
└── build.gradle     # Gradle config (Java 21 toolchain)
```

---

## 🔧 Vanlige kommandoer

```bash
# Kjør alle tester
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew check

# Bygg uten tester (raskere)
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew build -x test

# Kun kompiler (uten å kjøre tester)
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew compileKotlin

# Start applikasjonen lokalt
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :infrastructure:bootRun
```

---

## 📋 Git Workflow

1. **Opprett feature branch fra main:**
   ```bash
   git checkout main
   git pull origin main
   git checkout -b feature/mitt-feature
   ```

2. **Gjør endringer og commit:**
   ```bash
   git add .
   git commit -m "feat: beskrivelse av endring"
   ```

3. **Kjør tester FØR push:**
   ```bash
   JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew check
   ```

4. **Push og opprett PR med automerge:**
   ```bash
   git push origin feature/mitt-feature
   gh pr create --title "feat: ..." --body "..." --label automerge
   ```

---

## 🧪 Tester

### Kjøre spesifikke tester

```bash
# En spesifikk testklasse
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :infrastructure:test --tests "*.IssueTicketIntegrationTest"

# En spesifikk testmetode
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :infrastructure:test --tests "*.IssueTicketIntegrationTest.shouldIssueTicketWhenQueueIsOpenAndSecretIsValid"
```

### Testinfrastruktur
- **PostgreSQL:** Testcontainers (automatisk)
- **Security:** Mocket via `TestSecurityConfig`
- **Email:** Mocket via `TestEmailConfig`

---

## 📁 Viktige filer

| Fil | Beskrivelse |
|-----|-------------|
| `ROADMAP.md` | Produkt-roadmap og features |
| `TEST-PLAN.md` | Integrasjonstest-plan |
| `MVP-TASKS.md` | Nåværende sprint-oppgaver |
| `PLAN.md` | Plan Limits implementasjon |

---

## 🚫 IKKE gjør dette

- ❌ Push direkte til `main`
- ❌ Kjør `./gradlew` uten `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`
- ❌ Commit uten å kjøre tester først
- ❌ Endre `build.gradle` uten god grunn

---

## 💡 Tips

1. **Worktrees:** Prosjektet har git worktrees i `worktrees/`. Jobb i hovedmappen, ikke i worktrees.

2. **i18n:** Alle bruker-synlige strenger skal i `messages*.properties`

3. **Templates:** Thymeleaf templates i `infrastructure/src/main/resources/templates/`

4. **Hexagonal arkitektur:** Domain → Application → Infrastructure. Avhengigheter går innover.

---

*Spørsmål? Se TEST-PLAN.md eller ping Astra 🛡️*
