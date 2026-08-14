# Access — what Tessera does now, and what adopting `jmouse-access` would look like

> ## ⚠️ Superseded. This is the argument, not the answer.
>
> **The adoption happened** (ticket 19, half one). Everything below is written in the future tense and
> should be read as the case that was made, not as a description of the product. What is actually in
> force is `src/main/resources/policy/tessera.jmp`, and the reasoning that survived is in
> `TesseraScope`, `AccessAxis` and `AccessVocabularyConfiguration` — beside the code, where it cannot
> drift.
>
> **Three of its proposals were rejected on the way**, and the reasons are worth more than the
> proposals: `@BOARD` is a synonym for its project (a board is one-to-one with one, so a grant there
> would cover exactly what a grant at the project covers), `@ORGANIZATION` names a row Tessera does not
> have, and the permission names had to change shape entirely — a policy document cannot express
> `BROWSE_PROJECT`, because the parser identifies a permission *by* its colon.
>
> ⚠️ **`tessera.jmp` in this directory is not loaded and must not be edited as though it were.** Two
> files of the same name, one live and one not, is exactly the confusion the whole cluster exists to
> remove; it is kept only because the version history of an argument is worth something.

**Nothing in this directory runs.** Tessera authorised locally and correctly at the time this was
written; this is the worked example attached to `ACCESS-HANDOVER.md`, kept so the adoption argument can
be read rather than imagined.

| File | What it is |
|---|---|
| [`tessera.jmp`](tessera.jmp) | Tessera's access written as a policy document — speculative, and deliberately outside `src/main/resources/policy/` |
| `../../../ACCESS-HANDOVER.md` | The adoption plan: what to build, in what order, and the three things that go wrong quietly |

## What Tessera already gets right

`ProjectPermissionService` resolves `role permissions ∪ ALLOW overrides − DENY overrides`, with
**deny winning** — which is the load-bearing half of the model and the half most products get wrong.
It also refuses to let a member without `BROWSE_PROJECT` see anything, so membership is already
"an empty set at a place" rather than a separate check.

⚠️ **Adoption is not a fix.** Nothing here is broken. What the engine adds is *shapes Tessera cannot
currently express*, and the honest question is whether any of them is wanted yet.

## The four it cannot say

| Shape | Today | With the engine |
|---|---|---|
| **"Edit what you raised"** | `EDIT_ISSUE` is project-wide: everybody's issues or nobody's | `@SELF EDIT_ISSUE` — the narrowest width of the same axis |
| **"This board and no other"** | the narrowest grantable thing is a project | `@BOARD` — one floor further down |
| **"Every project this account owns"** | one membership row per project, created by whoever remembers | assignable `@ORGANIZATION`, carried `@PROJECT` |
| **"Except when the sprint is closed"** | an override is unconditional — on or off | a `when { … }` on the grant |

The last one is the one worth arguing about: deleting an issue out of a closed sprint silently
rewrites every burndown and every velocity number that sprint appears in. Today nothing can stop an
administrator doing it.

## What it would cost

- **`TesseraScope`** and a `ScopeCatalog` bean — five values, declaration order is width order
- **One `ScopeHierarchy`** — board ⊂ project ⊂ account, in both directions
- **A `GrantStore`** over the existing role assignments and overrides, or none if every grant moves
  into the document
- **Gating every endpoint**, with a build failure for any handler under `/api` that declares nothing —
  that rule is what makes the model exhaustive rather than aspirational

⚠️ **The acceptance test is a diff**: zero lines changed in `jmouse-access`, `jmouse-access-policy`,
`jmouse-access-el`, `jmouse-access-spring-boot`. Innoventa's adoption spent seven, three of which were
invisible to the compiler. Expect to find some — `@BOARD` is a floor no adopter has exercised.

## What can wait indefinitely

The **entitlement** half. The axes are independent: a product with no `CapabilityCatalog` has no
entitlement axis and nothing refuses. `tessera.jmp` sketches `capabilities { }` and `plans { }` to
show the shape, and none of it is worth building until Tessera sells something.

## See also

- `Innoventa/BE/docs/access/README.md` — the model itself
- `Innoventa/BE/docs/access/PATTERNS.md` — the same mechanisms in five unrelated industries
- `Innoventa/BE/docs/adr/0017-*` and `0018-*` — the two decisions behind it
