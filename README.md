# CatERing — Gestione degli Eventi
**Sviluppo delle Applicazioni Software (SAS) — a.a. 2025/2026**

### Autori
* **Alessandra Tilloca** — 1098173
* **Elis Qose** — 1094455

---

## 📁 Struttura del Repository

Il repository è strutturato in due componenti principali:

```text
SAS-25-26/
├── allegato-tecnico/          # Documentazione tecnica (LaTeX, PlantUML, Diagrammi, PDF)
│   ├── capitoli/              # Sorgenti LaTeX dei capitoli (Glossario, UC, Contratti, ecc.)
│   ├── diagrams/              # Sorgenti PlantUML (.puml) e Umlet (.uxf)
│   ├── img/                   # Diagrammi renderizzati (PNG, PDF)
│   ├── out/                   # Output di compilazione (Allegato Tecnico.pdf)
│   ├── build.py               # Script di build completo (PlantUML -> PNG -> PDF)
│   └── main.tex               # Documento principale LaTeX
│
└── catering/                  # Implementazione software in Java
    ├── database/              # Schema SQLite e file database
    ├── src/
    │   ├── main/java/         # Package di business logic e persistence
    │   └── test/java/         # Test di unità
    └── pom.xml                # Configurazione Maven
```
---

## 📄 Allegato Tecnico

L'allegato tecnico include l'intero ciclo di analisi e progettazione:
1. **Glossario**
2. **Caso d'Uso Dettagliato** (Gestione degli Eventi)
3. **Modello di Dominio**
4. **Diagrammi di Sequenza di Sistema (SSD)**
5. **Contratti delle Operazioni**
6. **Diagramma delle Classi di Progetto (DCD)**
7. **Diagrammi di Sequenza di Progetto (DSD)**

### Compilazione dell'Allegato Tecnico

Lo script `build.py` gestisce automaticamente:
* Il download e l'esecuzione di PlantUML (`plantuml.jar`)
* La conversione dei diagrammi UML in immagini ad alta risoluzione
* La compilazione LaTeX con `pdflatex`
* Il salvataggio del documento finale in `allegato-tecnico/out/Allegato Tecnico.pdf`

---

## Progetto Java (`catering`)

Il modulo `catering` contiene l'implementazione Java del sistema:
* **Business Logic:** Gestione di eventi, compiti di cucina, menu, ricette, turni e utenti.
* **Pattern Architetturali:** Facade, Command, Observer / Event Receiver.
* **Persistenza:** SQLite con gestione transazioni e batch update (`catering.db`).
* **Test:** Suite di test di unità JUnit.

### Esecuzione e Test
Il progetto è gestito tramite **Maven**.