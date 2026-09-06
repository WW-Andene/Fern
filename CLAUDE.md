# Project Rules — Master Template

Read this before making any code, UI, or styling change. These are hard rules, not suggestions — do not bypass them by reaching for a default utility class, an arbitrary one-off pixel value, a "close enough" number, or a lazy workaround.

Rules are grouped into domains below, each grounded in an established professional discipline rather than ad hoc preference. Within a domain, more specific rules refine — never override — the general ones above them.

---

## 1. Work Habits — process discipline

Discipline: **quality assurance / engineering process discipline** (cf. the verification and root-cause-analysis practices underlying ISO 9001 process control and standard code-review methodology).

1. **Verify before and after every finding or fix (pre-/post-verification).** Confirm the defect is real, reproducible, and understood before acting on it; confirm the fix actually resolves it afterward via a positive check (a passing test, a reproduced-then-cleared repro case, a before/after diff of the actual numbers). Neither end is ever assumed.
2. **Cross-check and corroborate sources.** Do not rely on a single read, a hand-copied reimplementation, or a remembered assumption. Verify claims against the authoritative source — the real file, the real test, the real data — before relying on them. If a hand-rolled diagnostic script disagrees with the real code path, trust the real code path and find out why the script was wrong, not the reverse.
3. **Perform due diligence on source data, and apply it consistently.** Read and understand reference data thoroughly before using it, and apply that understanding at every decision point in the task where it is relevant — not as a one-time lookup consulted only when convenient.
4. **Prefer root-cause remediation over palliative workarounds.** Where a genuine, sourced fix is available, implement it rather than deferring, suppressing, or working around the underlying issue. This is standard root-cause-analysis practice: a workaround treats a symptom and leaves the defect live.
5. **State uncertainty explicitly; never present a guess as a verified fact.** If something wasn't checked, say so. A confident-sounding but unverified claim is a worse outcome than an honest "I haven't confirmed this yet."
6. **Ask rather than assume when a decision materially changes behavior, scope, or risk.** Use judgment for reversible, low-stakes calls; escalate for anything hard to reverse, ambiguous in intent, or outside the request's stated scope (see §5, Change Scope Discipline).

### 1.1 Definition of Done

A task is not complete until all of the following hold — treat this as the exit checklist for any non-trivial change:

- The root cause (not just the symptom) is identified and addressed, per §1.4.
- The fix is verified against real execution (tests, a reproduced scenario, or measured output), not just read-through review.
- The full relevant test suite passes — not just the test for the immediate change; a regression elsewhere is still a regression.
- Any newly-introduced non-obvious behavior, tradeoff, or known limitation is documented **in the code or commit message**, not only explained conversationally. If asked "did you document it?", the answer should already be yes.
- The diff is reviewed for scope: no unrelated cleanup, no drive-by refactors, no speculative abstractions bundled into the same change (see §5).
- Naming and structure of anything touched or added conform to §4.

### 1.7 Skill or playbook usage — invoke the right one proactively, don't wait to be asked

If this project defines reusable skills, playbooks, or checklists for recurring kinds of work (an audit procedure, a restructuring procedure, a design-review procedure, a scope-disambiguation procedure), recognizing which situation calls for which one — and invoking it without waiting for the user to name it — is itself part of doing the work well. Treating them as optional extras that only run when explicitly requested is how audits get skipped and drift accumulates.

List each such skill/playbook here with the file or command that invokes it and the concrete trigger conditions for using it, in a table like:

| Skill/playbook | Location | Invoke when |
|---|---|---|
| *(e.g. a broad app-wide audit)* | *(its file/command)* | *(the trigger conditions that should call it, stated concretely enough that "I should have run this" is checkable after the fact)* |
| *(e.g. a code-quality-specific audit)* | | |
| *(e.g. a structural-reorganization procedure)* | | |
| *(e.g. a visual/aesthetic-specific audit)* | | |
| *(e.g. a scope/ambiguity-resolution procedure)* | | |

**How this interacts with the rest of this document:**
- Any maintenance cadence defined elsewhere in this document (e.g. §4's "every ~50 commits") is a *floor*, not the only trigger — a listed skill should run sooner whenever its specific trigger condition is hit mid-task.
- Running an overly broad skill when a narrower one actually fits wastes the same effort §5 (Change Scope Discipline) warns against — match the skill to the actual scope of concern, not the biggest hammer available.
- These skills are companions to, not substitutes for, §1.6 (ask rather than assume): a skill's own verification gates do not remove the obligation to flag a genuinely ambiguous or high-stakes call to the project owner.

---

## 2. Verification & Testing Discipline

Discipline: **software testing and validation methodology** — the practice of treating automated tests and reproducible measurements as the primary evidence of correctness, not narrative confidence.

1. **Run the real test suite before making a change and after.** A pre-existing failure is not yours to silently absorb into your diff's "before" state without noting it; a new failure after your change is yours to explain or fix before calling the task done.
2. **Prefer measured evidence over inferred behavior.** When a value, a rate, or a count matters, obtain it via the actual code path (a temporary, always-reverted diagnostic if needed) rather than computing it by hand from assumptions about how the system behaves.
3. **When a change causes divergence from a previous baseline (a golden file, a snapshot, a fixture), never widen the tolerance or update the baseline without first establishing why the divergence is expected.** Document the specific, cited reason next to the widened tolerance or updated fixture — a bare "widened for now" is not acceptable.
4. **Delete throwaway diagnostics.** Debug-only instrumentation (temporary throws, dumps, one-off scripts) is removed once it has served its verification purpose — it does not linger in the codebase or get mistaken for permanent code.
5. **A test suite passing is necessary, not sufficient, for UI/UX work.** For anything user-facing, exercise the actual feature (start the app, interact with it) before reporting success — passing type checks or unit tests verifies correctness of code, not correctness of experience.

---

## 3. Security — access control & least privilege

Discipline: **information security / access control**, specifically the principle of least privilege and change-authorization control found in frameworks such as ISO/IEC 27001 and NIST SP 800-53 (access control family).

Do not unilaterally provision any privileged access path — debug bridges, backend doors, or equivalent — without explicit authorization from the project owner. If diagnosing a defect appears to require this class of access, stop and request authorization first. Do not enable it preemptively, present it as a fait accompli, or reclassify it as "temporary" or "diagnostic" to sidestep this control.

---

## 4. Code Hygiene, Naming & Structure — records & configuration management

Discipline: **records management and information architecture** (the classification, naming, and lifecycle-maintenance practices formalized in ISO 15489) applied to source code, combined with standard **software configuration management** practice (naming-convention and code-organization guidance as codified in style guides such as Google's engineering style guides and Clean Code's naming principles).

Structuring, organizing, segmenting, naming, and classifying files, folders, and code is **mandatory and non-negotiable**, regardless of deadline, urgency, or how small a change seems. A quick fix does not excuse misplacing a file, reusing a misleading identifier, or leaving a new symbol unclassified "for now."

Hygiene, ownership clarity, coherence, and — above all — **consistency** (of naming, of structure, and of process) are a standing priority that no task, deadline, or request implicitly waives. If a request would require violating this, flag the conflict before proceeding rather than complying silently.

The rest of this section makes that mandate concrete: where a new file or folder goes, what it must be named, and what is and isn't allowed to exist as a standalone file — rather than leaving "structure it properly" as an unstructured instruction. Populate the tables and examples below with this project's own conventions the first time this template is adopted; keep the structure (taxonomy → naming → persistent-documentation homes → new-top-level-entry gate) intact.

### 4.1 Directory taxonomy — where a new file goes

Every codebase should be organized by **purpose**, not by file type. There are two dominant, well-established conventions in professional software engineering for what "purpose" means structurally — pick the one that fits the project's shape (or the one the project already follows) and apply it consistently; do not mix both in the same codebase.

**Convention A — Layered / Clean Architecture** (Robert C. Martin's *Clean Architecture*; also called Onion or Hexagonal Architecture). Organizes by how far a piece of code sits from business rules vs. the outside world. Standard layer names used industry-wide:

| Layer | Purpose | Typical directory |
|---|---|---|
| Domain / entities | Core business rules and data structures, with zero dependencies on anything else in the codebase | `domain/`, `entities/`, `core/` |
| Use cases / application | Application-specific business logic that orchestrates the domain | `usecases/`, `application/`, `services/` |
| Interface adapters | Translates between use cases and the outside world (controllers, presenters, view models, repositories' interfaces) | `adapters/`, `controllers/`, `viewmodels/` |
| Frameworks & drivers | The outermost layer: UI, database, web framework, external APIs — the most replaceable, most volatile code | `ui/`, `infrastructure/`, `data/`, `api/` |
| Every automated test | Mirroring the package/module of the code under test, colocated or centralized (pick one convention and apply it everywhere) | `tests/`, or colocated `*.test.*`/`*Test.*` files |
| Golden/reference fixtures a test compares against | A dedicated `fixtures/` directory beside the tests that use them | `fixtures/` |

**Convention B — Feature-based / package-by-feature** (the dominant convention in modern frontend and mobile codebases — React, Vue, Android/Kotlin, Flutter communities all converge on this). Organizes by product feature first, with a thin shared layer underneath:

| Purpose | Typical directory |
|---|---|
| App-wide singleton services with no UI (auth, networking, persistence) | `core/` |
| Code reused across more than one feature (shared UI components, utilities, constants) | `shared/` or `common/` |
| A self-contained product feature's own components/screens and logic | `features/<feature>/` |
| Generic, framework-agnostic utility functions with no app-specific knowledge | `utils/` |
| Every automated test | Mirroring the package of the code under test |
| Golden/reference fixtures a test compares against | A dedicated `fixtures/` directory beside the tests |

**Decision procedure, in order:**
1. If the project already has an established convention (check for existing `domain/`/`usecases/` vs. `features/`/`shared/` directories before adding anything), follow it — do not introduce the other convention alongside it.
2. If starting fresh: Convention A suits systems with substantial, framework-independent business logic (backends, domain-heavy services); Convention B suits UI-heavy apps organized around discrete user-facing features (most frontend/mobile apps). State which was chosen and why.
3. Does an existing directory already match this artifact's purpose? Use it.
4. Is it a genuinely new *kind* of purpose neither table covers (not just a new instance of an existing kind)? Creating a new top-level directory is itself a structural decision — flag it and get confirmation before creating it, rather than deciding unilaterally.
5. Never create a "misc," "helpers," "common," or "stuff" catch-all directory. If something doesn't fit the taxonomy, that is a signal to ask, not to invent a dumping ground.

### 4.2 Naming conventions — how a new file is named

Naming is not a stylistic afterthought; a misleading or inconsistent name is a hygiene violation on the same footing as a misplaced file. Use the ecosystem's own established convention rather than inventing one — mixing conventions within one language/ecosystem is itself a violation:

- **JavaScript/TypeScript:** `camelCase` for variables/functions, `PascalCase` for classes/components/types, `UPPER_SNAKE_CASE` for true constants. File names: `PascalCase.tsx` for a file whose default export is a component; `camelCase.ts` for a plain module; some style guides (e.g. Angular's) instead use `kebab-case.component.ts` — pick one per the framework's own convention, not ad hoc.
- **Python:** `snake_case` for variables/functions/modules/files, `PascalCase` for classes, `UPPER_SNAKE_CASE` for constants (PEP 8 — the language's own official style guide).
- **Kotlin/Java:** `PascalCase` for classes/interfaces/objects (one public type per file, file named identically to it), `camelCase` for functions/properties/variables, `UPPER_SNAKE_CASE` for `const val`/`static final` constants.
- **Swift:** `PascalCase` for types, `camelCase` for everything else — per Apple's official Swift API Design Guidelines.
- **Hooks/composables** (React/Vue): a consistent prefix (`use`, per the framework's own convention), named after the state/behavior they encapsulate.
- **Plain modules:** named after the single responsibility of the module, not after the ticket, task, or person that produced it — never a generic `utils.ts`/`helpers.py`/`Misc.kt`.
- **Domain entities with a stable identifier** (e.g. per-item configuration files keyed to a real-world id): the identifier's canonical spelling and casing, used identically everywhere it appears — never a second spelling for the same entity.
- **Tests:** `<Subject>.test.ts` / `test_<subject>.py` / `<Subject>Test.kt` — whichever suffix/prefix convention the language's own test tooling expects — so the subject is identifiable without opening the file.
- **Fixtures:** named after their subject, living beside/under the tests that use them, never inline-duplicated elsewhere.
- **Localized variants of the same data:** the exact same base name as the source-language file, distinguished only by a locale suffix (e.g. ISO 639-1 codes: `strings.en.json`, `strings.fr.json`), in the same directory.
- **Git branches:** `<type>/<short-description>` where `<type>` matches the Conventional Commits types in §9 (e.g. `feat/inline-editing`, `fix/null-pointer-on-empty-list`) — a widely adopted convention, not a project-specific invention.

### 4.3 Persistent documentation — where it lives, and what "persistent" means

This is the rule that exists specifically to stop notes, logs, and one-off summaries from accumulating across the repo over time.

**A finding, decision, or piece of reasoning that must survive the current task belongs in exactly one of these, and nowhere else:**
1. **A code comment**, at the exact line the reasoning concerns, when the point is "why this specific line is the way it is."
2. **The commit message**, when the point is "why this change was made" and doesn't need to be visible to someone just reading the code later.
3. **An existing, purpose-named reference document already living beside the code it documents.** Extend one of these when the new information belongs to its existing subject, rather than starting a new file.
4. **A new reference document**, only when the information is a genuine, reusable deliverable with a clear subject and no existing home for it — named after that subject (not "notes" or "log"), placed beside the code it documents, and only after flagging its creation to the project owner, since a new standalone doc is a structural addition, not an incidental byproduct of the task at hand.

**What must never happen, because this is the specific failure mode this rule exists to prevent:**
- Creating files like `NOTES.md`, `TODO.md`, `progress.md`, `fix-log.md`, `investigation.md`, `summary.md`, or any other generically-named scratch or status file inside the repository, at any location, for any reason. If you want to remember something across steps of the same task, use an out-of-repository scratch location — never a tracked file.
- Leaving a diagnostic-only script or test in the tree after it has served its verification purpose (see §2.4). A temporary diagnostic test, a debug-only trace, or a hand-rolled reproduction script is deleted before the task is considered done — it does not get committed "just in case," and it is never a substitute for one of the four permanent homes above.
- Writing the same finding into more than one of the above without reason — pick the one destination that fits, rather than a code comment *and* a new doc *and* a chat explanation of the same fact.

### 4.4 Module boundaries — allowed dependency directions

A directory taxonomy only holds if files placed correctly are also only *importing* from directories they're allowed to depend on. Without this, a low-level module can end up importing from a feature, two features can couple to each other directly, and the whole taxonomy in §4.1 becomes decorative. This is governed by the **Dependency Rule** (Robert C. Martin, *Clean Architecture*): source code dependencies can only point *inward*, toward higher-level policy — an inner layer must never know anything about an outer one.

If following **Convention A** (§4.1), the rule maps directly onto the layer table:

```
domain/           →  (depends on nothing else in the source tree)
usecases/         →  domain/
adapters/         →  usecases/, domain/
ui/, infrastructure/, data/  →  adapters/, usecases/, domain/
```

If following **Convention B**, the equivalent shape is:

```
utils/    →  (depends on nothing else in the source tree)
core/     →  utils/
shared/   →  core/, utils/
features/<feature>/  →  shared/, core/, utils/
```

Concretely, under either convention:
- **A lower layer never imports from a higher one.** If a lower-layer module seems to need something from a higher layer, that's a sign the shared piece belongs in the lower layer instead — move it down, don't import up.
- **The cross-feature-reuse layer (`shared/`/`adapters/` or equivalent) never imports from a specific feature.** The moment it depends on one feature, it is no longer shared — either the code isn't actually generic and belongs in that feature, or the feature-specific part must be extracted out first.
- **One feature never imports another feature's internals directly.** Cross-feature reuse goes through the shared layer, promoting the reused piece there first.
- **A subsystem's internals stay internal to it.** Code outside a subsystem interacts with it through its published entry points (e.g. a barrel/index file, a public interface), not by reaching into files it doesn't own — this is the standard **information hiding** principle (Parnas).

When in doubt about which layer a file belongs to, its allowed imports are the answer: a module that needs to import from a feature cannot live in a lower layer, no matter how "core" its purpose feels.

### 4.5 File decomposition — when a file must be split

A file is a unit of hygiene, not just of code. Split a file once any of these thresholds is crossed, rather than letting it grow indefinitely:

- **It serves more than one responsibility.** If describing the file's purpose in one sentence requires "and," the responsibilities are separable — split along that seam.
- **Its tests need multiple, unrelated top-level test groups** to cover genuinely different concerns rather than different cases of the same concern. That's a proxy for the file itself covering more than one concern.
- **A change to one part of the file routinely has no effect on, and no relation to, another part of the same file.** Unrelated change-reasons are a decomposition signal (the same principle behind the Single Responsibility Principle).
- **It has grown large enough that a reader must scroll past unrelated code to find the part relevant to their task.** This is a readability failure, not a badge of thoroughness.

When splitting, keep the resulting files inside the same directory (per §4.1) unless the split reveals a genuinely new purpose category, and preserve the naming convention in §4.2 for each resulting file — a split is not an excuse for an ad hoc name.

### 4.6 Generated files and barrel/index files

- **Generated output is never hand-edited.** Anything under a directory explicitly designated for build output is produced by an explicit generation step. If it needs to change, change the generator and regenerate — editing the output directly creates silent drift the next time it's regenerated, and is treated as a hygiene violation, not a shortcut.
- **Barrel/index files re-export; they do not implement.** Where this project uses aggregation files, they collect and re-export the real modules in that directory. New logic never gets written directly into a barrel file; it goes into a properly named module that the barrel then re-exports.

### 4.7 Deprecation and deletion — retiring a file or dead code correctly

Removing code is as much a structural act as adding it, and gets the same rigor rather than being treated as a free action:

1. **Confirm zero remaining references** before deleting a file or export — search the codebase, don't rely on memory of what you think still uses it (per §1.2, corroborate before acting).
2. **Migrate consumers first, delete second**, in that order within the same piece of work — never leave a codebase in a state where both the old and new paths are live "just in case" beyond the migration itself.
3. **No commented-out code as a soft delete.** Dead code is removed outright; version control, not a comment block, is the record of what used to be there. A comment explaining *why* something was removed is fine; the removed code left in place as a comment is not.
4. **A deletion that removes an entire file's worth of exports is its own reviewable change** (see §5, Change Scope Discipline) unless it's the direct, necessary conclusion of the migration it follows.

### 4.8 New top-level files and directories

The repository root and the top level of the source tree are reserved for canonical, whole-project artifacts (a README, this rules file, and the top-level source directories in §4.1). Adding anything new at either level — a new root document, a new top-level source directory — is a structural decision on the same footing as changing the taxonomy itself: flag it and get confirmation before creating it, don't add it as a side effect of an unrelated task.

### 4.9 Enforcement — structural rules should be checkable, not just followed

Prose discipline alone is a weaker guarantee than a rule a tool can actually verify. Where practical, back the rules above with something mechanical rather than relying solely on manual compliance:

- A lint rule or dependency-boundary checker (e.g. an import-boundary ESLint configuration, or a dependency-graph tool) to catch a §4.4 layering violation automatically.
- A naming-convention check extended to cover each artifact type from §4.2 as it's introduced.
- Treating the absence of such tooling as a gap to close during the maintenance cadence below, not a permanent excuse to fall back on discipline alone.

**Maintenance cadence (lifecycle management):** run a project restructuring/audit skill (if one exists) and a code-audit pass on a regular cadence — as a floor, every ~50 commits — to prevent structural drift from accumulating. This is scheduled maintenance, analogous to a records-retention review, not something to defer until requested.

**Standing exceptions:** any file or module the project owner designates off-limits should be listed here explicitly (path, scope of what's covered, and the exact condition under which it may be touched). Do not touch such files outside that condition, even for this maintenance cadence, an unrelated defect fix, or an adjacent "quick" change.

---

## 5. Change Scope Discipline

Discipline: **change management** — keeping each change unit minimal, reviewable, and traceable to a single intent, per standard configuration-management practice.

1. **Match the diff to the request.** Do not bundle unrelated refactors, renames, or "while I'm here" cleanups into a change whose purpose is something else — file such improvements as their own follow-up instead.
2. **No speculative generality.** Do not add abstractions, configuration options, or extensibility hooks for hypothetical future needs. Build for the requirement in front of you.
3. **Keep commits atomic and their messages accurate.** A commit message describes what actually changed and why; it is not aspirational or a summary of unrelated work swept in alongside.
4. **When a fix surfaces a second, adjacent issue**, decide deliberately whether it belongs in the same change (it directly blocks correctness) or a separate one (it is independently valuable but not required) — and say which, rather than silently expanding scope either way.

---

## 6. App & Feature Development — functional completeness

Discipline: **requirements engineering and interaction design** — specifically the *completeness* criterion for a well-formed requirement (ISO/IEC/IEEE 29148: a requirement set is incomplete if it omits functionality the user needs but didn't think to state explicitly), combined with the standard interaction-design practice of matching a UI request to its recognized **component archetype** rather than only its literal wording, and with Nielsen's usability heuristics — in particular *user control and freedom* and *consistency and standards*, which are exactly what's missing when a feature is built with no way to operate it fully.

This section exists because a request named after a familiar kind of feature ("a color selector," "a music player," "a settings panel") carries an implicit spec far larger than its literal words: real-world convention already defines what that archetype minimally includes. Implementing only the literally-named mechanic and skipping the rest is an **incompleteness defect** — a feature is not done because it compiles and shows something; it is done when it satisfies the implicit requirements of the archetype actually asked for, per Definition of Done (§1.1) and §2 (verification against real execution) — not just its most literal reading.

### 6.1 Resolve the request to its component archetype before building

Before writing any code for a named feature, identify what that request actually names: a recognized class of UI/interaction pattern with well-established conventions, not just the single mechanic that makes it distinctive. Two features can share a name and still differ in exact needs, but the baseline set is what makes something recognizable as that archetype at all — omitting from it isn't a smaller version of the feature, it's a different, broken thing wearing its name.

Representative examples (non-exhaustive — apply the same reasoning to any archetype not listed):

| Requested feature | The literal, distinctive mechanic | The implicit baseline the archetype also requires |
|---|---|---|
| A color selector/picker | A way to choose a color value | A trigger to open it, a way to confirm/apply the choice, a visible current-value indicator, and a wired destination that actually receives the chosen value |
| A music/audio player | Play/pause of a track | Volume control, a way to mute, a progress/seek indicator, and the actual audio source wired in — a player with nothing to play is not a player |
| A settings panel | The individual settings/toggles | A way to open/close or navigate to it, persistence of the choices made, and each setting actually wired to the behavior it claims to control |
| A form | The input fields | Validation feedback, a submit action, a cancel/dismiss path, and a wired destination for the submitted data |
| A modal/dialog | The content inside it | An open trigger, a close affordance (explicit control, not just an assumed outside-click), and a defined return path for whatever the modal was for |

### 6.2 Verify data flow end-to-end, not just the presence of a control

A control that exists but does nothing, or a value that is produced but never consumed, is exactly the same class of defect as a missing control — both leave the feature functionally incomplete. Before calling a feature done:
1. Trace the full path from user input to observable effect (a color chosen is applied and/or stored somewhere real; a volume slider actually changes the audio output level; a submitted form actually reaches its destination).
2. Confirm there is no dangling half: no UI control with no wired effect, and no wired capability with no UI control to reach it.
3. This is the feature-level instance of §2.1/§2.5 (verify against real execution; a passing build is not evidence the feature works) — exercise the actual interaction, don't infer wiring from the code looking plausible.

### 6.3 This is not a license for scope creep — reconcile with §5

§5.2 ("no speculative generality... build for the requirement in front of you") and this section are not in tension, and neither overrides the other:

- **§5.2 forbids building for hypothetical future requirements** — options, hooks, or abstractions nothing currently asks for.
- **§6 requires building the actual current requirement completely** — the baseline parts in §6.1 are not hypothetical or future; they are already implied by the feature named, whether or not each part was said out loud. Skipping them isn't restraint, it's under-delivery against what was actually asked.

The line between the two: if a real-world instance of the named archetype would be considered broken or unusable without a part, that part is in scope now, per §6.1 — not a speculative extra.

### 6.4 When the archetype's baseline is genuinely ambiguous or intentionally reduced

If a request explicitly scopes a feature down ("just the color swatches, no picker UI yet") or the right baseline for an unfamiliar/novel archetype isn't obvious, don't guess silently in either direction — per §1.6, ask, and state explicitly which parts of the usual baseline are being deliberately deferred and why, so the gap is a documented decision rather than an unnoticed omission.

---

## 7. Design System — design-token governance

Discipline: **design-token systems**, the standard mechanism (used across major design systems, e.g. Material Design, Salesforce Lightning) for enforcing a single source of truth for spacing, sizing, and typography scales, ensuring pixel-accurate consistency across a UI.

This template defaults to the **8-point grid system** — the dominant real-world spacing standard, used across Material Design (Google), Carbon (IBM), Polaris (Shopify), and most professional design systems — rather than an arbitrary invented scale. Swap it only if the project's platform mandates a different base unit (see the Android/`dp` note below); keep the same enforcement structure either way.

Every numeric spacing/sizing dimension in the app — width, height, padding, margin, gap, icon size, border-radius input — **must** be a multiple of the base unit, **4**, with **8** preferred wherever the value is large enough to use it:

```
4  8  12  16  24  32  40  48  64  80  96  128
```

(4 fills small gaps — icon padding, border widths, hairline gaps; 8 and its multiples cover everything else. This is the standard "4/8 spacing scale" documented across the design systems named above.) No other number is permitted. This applies to existing code as well as new code — when an off-scale value is touched, correct it to the nearest scale value as part of that change rather than leaving it.

Font sizes follow a **modular type scale** — a geometric progression from a base size by a fixed ratio, the standard typographic technique for a coherent hierarchy (see any of the well-known ratios: Minor Third 1.2, Major Third 1.25, Perfect Fourth 1.333). Pick one ratio for the project and derive every font size from it rather than choosing sizes ad hoc; most UI frameworks' own default type scales (Material Type Scale, Tailwind's default font-size scale) are already built this way — prefer the platform's own scale over hand-rolling one.

**Before setting any dimension:**
- Never assume a default utility class is compliant — verify its computed px/dp value against the defined scale. A framework's default spacing/typography scale will usually already comply (most professional frameworks use 4/8-based spacing internally) but confirm rather than assume.
- If a token (`--font-*`, `--size-*`, `--space-*`, a design-system's own spacing tokens, etc.) already resolves to a scale value, use it — this is the token layer doing its job.
- If the nearest existing token is off-scale, either correct the token (when the change should propagate to every usage) or apply an explicit, scoped override for just that element (when the change is local only).
- **Platform exception:** on Android, use `dp` (density-independent pixels) as the unit the 4/8 grid applies to, and take font sizes from the platform's own Material 3 type scale (`MaterialTheme.typography.*`) rather than raw `sp` literals — this is Android's own established convention, not a deviation from the rule.

### 7.1 Rounding priority (tie-breaking)

When correcting an off-scale value, round to the nearest multiple of 4. On an exact tie (e.g. `10` is equidistant between `8` and `12`), round up to the larger value — simplest, most common tie-break convention, and one a linter can check unambiguously.

### 7.2 Larger spacing values

Above roughly 64, prefer the standard doubling steps (`64, 80, 96, 128, 160, 192, 256`) over an arbitrary multiple of 4, so large gaps read as intentional layout rhythm rather than incidental math.

### 7.3 Corner radius

Use the design system's own shape/radius tokens where the platform provides them (e.g. Material 3's `small`/`medium`/`large`/`extraLarge` shape scale) rather than a hand-computed radius. Where no such token set exists, define one small fixed set for the whole project (e.g. `4, 8, 16, 9999` for "sharp / soft / rounded / pill") and round any candidate radius to the nearest of those, per §7.1's tie-break rule — never a one-off radius picked to look right locally.

### 7.4 Aspect ratios — preferred, not mandatory

Default to the standard photographic/UI ratios rather than an arbitrary crop: `1:1` (avatars, thumbnails), `4:3` and `3:2` (general imagery), `16:9` (video/wide banners). Not a hard constraint — apply engineering judgment rather than forcing a mismatch — but a deliberate choice from this list, not an incidental crop.

### 7.5 Design objectives behind the design-token system

Standardization · coherency · consistency · pixel-accurate precision · symmetry · disciplined aesthetic proportion · deliberate, documented art-direction decisions — never a default or "close enough" value.

---

## 8. Versioning & Commit Conventions

Discipline: **release management and change traceability** — the two most widely adopted real-world standards for this are used here rather than an invented scheme, so tooling (changelog generators, release automation) works out of the box.

1. **Commit messages follow Conventional Commits** (`https://www.conventionalcommits.org`): `<type>[optional scope]: <description>`, e.g. `fix(canvas): correct stroke width scaling at high zoom`. Standard types: `feat` (new capability), `fix` (bug fix), `refactor` (no behavior change), `docs`, `test`, `chore`, `perf`, `build`, `ci`. A breaking change is marked with `!` after the type/scope (`feat(api)!: ...`) or a `BREAKING CHANGE:` footer.
2. **Releases follow Semantic Versioning** (SemVer, `https://semver.org`): `MAJOR.MINOR.PATCH`. Increment `MAJOR` for a breaking change, `MINOR` for a backward-compatible new feature, `PATCH` for a backward-compatible fix. Pre-1.0.0 (`0.x.y`) signals the public API/behavior is not yet considered stable and may still break on a `MINOR` bump — move to `1.0.0` deliberately, when that stability commitment is actually intended.
3. **A CHANGELOG, if the project keeps one, follows Keep a Changelog** (`https://keepachangelog.com`): grouped under `Added`/`Changed`/`Deprecated`/`Removed`/`Fixed`/`Security`, newest release first, with an `[Unreleased]` section at the top for work not yet cut into a release.
4. **Branching model:** default to **trunk-based development** (short-lived branches off the main trunk, merged frequently, per §5's atomic-commit discipline) unless the project has an established reason to run a heavier model (e.g. Git Flow's `develop`/`release`/`hotfix` branches for a project with scheduled, versioned releases rather than continuous deployment) — state which model applies here in §9 rather than leaving it ambiguous.

---

## 9. Project-Specific Standards

Use this section to record any reference proportions, component precedents, or exceptions unique to this project (e.g. header/navbar reference dimensions, known off-scale tokens not yet fixed, which branching model from §8.4 this project actually uses, other standing exceptions). Keep such content isolated here so it stays easy to identify and to strip out when reusing this document as a template for a different project.
