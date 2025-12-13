# WebFX Unified CSS v1 — What the CLI Generates for Web and JavaFX

This document summarizes how the WebFX CLI consumes unified CSS files (named `*-webfx@main.css` and other `*-webfx@*.css`) and produces the two platform-specific stylesheets:

- Web output: plain web CSS tailored for the WebFX single-host strategy
- JavaFX output: translated CSS compatible with OpenJFX’s CSS engine

It is intended as a practical guideline for authors of unified CSS in WebFX.

## Inputs and Outputs

- Input files: any CSS file whose name contains `webfx@` (for example: `app-webfx@main.css`). These are included in BOTH web and JavaFX targets.
- Output file name: the `@...` suffix is stripped in the generated resources (e.g., `app-webfx@main.css` → `main.css`).
- File headers: the CLI prepends a comment header indicating the source path and module.
- Validation: unified CSS is validated against the v1 subset before translation. Validation severity can be controlled with the system property `-Dwebfx.css.validation=error|warn|ignore`.
- @font-face ordering (JavaFX only): any `@font-face` rules are moved to the top of the file to work around an OpenJFX loading quirk.

## Selector transformations

Only the JavaFX output transforms selectors; the web output keeps your selectors as-is (except for property moves described later).

- `fx-<tag>` type selectors map to JavaFX type selectors (examples):
  - `fx-text` → `Text`
  - `fx-label` → `Label`
  - `fx-button` → `Button`
  - In general, `fx-foo-bar` → the corresponding JavaFX class name if recognized. Otherwise left unchanged.
- `:root` (web) → `.root` (JavaFX style class) in selector headers.
- `.pseudo-selected` (web helper class) → `:selected` (JavaFX pseudo-class).

## Custom properties and var()

- JavaFX output:
  - Property names declared as CSS custom properties are rewritten: `--name: value;` → `-name: value;`
  - Usages are rewritten: `var(--name)` or `var(--name, fallback)` → `-name`
  - Standard `color:` property is mapped: `color: …;` → `-fx-text-fill: …;`
- Web output:
  - `color:` is rewritten to `--fx-text-fill:` to align with the default host rule (`color: var(--fx-text-fill)`)
  - Other custom property declarations/usages remain standard web CSS.

## Backgrounds

JavaFX output converts and normalizes the background family:

- Property name mapping:
  - `background-color` → `-fx-background-color`
  - `background-image` → `-fx-background-image`
  - `background-repeat` → `-fx-background-repeat`
  - `background-position` → `-fx-background-position`
  - `background-size` → `-fx-background-size`
- Shorthand expansion: `background: …;` is expanded into the longhands above, including layered backgrounds separated by commas.
- `repeat-x`/`repeat-y` are mapped to `repeat no-repeat` / `no-repeat repeat`.
- `linear-gradient(to top|right|bottom|left|… , …)` is converted to the JavaFX angle form.
- `border-radius` is duplicated to `-fx-background-radius` so the background clips as expected.

Web output keeps background properties as-is, with one addition:

- `border-radius: …;` is duplicated to `--fx-background-radius: …;` to support clipping behavior in WebFX.

## Borders

- JavaFX output:
  - Property name mapping:
    - `border-style` → `-fx-border-style`
    - `border-color` → `-fx-border-color`
    - `border-width` → `-fx-border-width`
    - `border-radius` → `-fx-border-radius`
  - Shorthand expansion: `border: <width> <style> <color>;` is expanded into `-fx-border-width`, `-fx-border-style`, `-fx-border-color`.
  - Per-side shorthands are expanded: `border-top|right|bottom|left: …`.
  - Per-side longhands are mapped: `border-*-width|style|color` → `-fx-border-*-width|style|color`.

- Web output (single-host strategy):
  - All border-related declarations are moved from the original selector to its `:before` pseudo-element. Other declarations remain on the original selector.
  - Example transformation:
    ```css
    /* input */
    .card { border: 1px solid #ccc; background: white; }

    /* web output */
    .card { background: white; }
    .card:before { border: 1px solid #ccc; }
    ```
  - The WebFX runtime styles the single host so that `:before` visually behaves like the element border.

## Fonts and text

- JavaFX output maps common typographic properties:
  - `font-family` → `-fx-font-family`
  - `font-size` → `-fx-font-size` (px values become unitless numbers as expected by JavaFX)
  - `font-weight` → `-fx-font-weight` (numeric weights are normalized to `normal`/`bold` conservatively)
  - `font-style` → `-fx-font-style` (`oblique` becomes `italic`)
  - `text-align` → `-fx-text-alignment`
  - `opacity` → `-fx-opacity`
  - The `font: …` shorthand is expanded into the mapped longhands.

Web output keeps font declarations unchanged (standard web CSS).

## SVG/Shape fills and strokes (JavaFX output)

- `stroke` → `-fx-stroke`
- `stroke-width` → `-fx-stroke-width`
- `stroke-linecap` → `-fx-stroke-line-cap`
- `stroke-linejoin` → `-fx-stroke-line-join`
- `fill` → `-fx-fill`

## Cursors (JavaFX output)

- `cursor` → `-fx-cursor`
- Keyword mapping:
  - `pointer` → `hand`
  - `grab` → `open-hand`
  - `grabbing` → `closed-hand`
  - `ew-resize` → `h-resize`, `ns-resize` → `v-resize`
  - `no-drop` / `not-allowed` → `disappear`
- `url(...)` cursors are not supported by JavaFX. The CLI emits a warning comment and falls back to `default`.

## Box shadow (JavaFX output)

- `box-shadow: offset-x offset-y blur [spread] color;` is translated to a JavaFX `-fx-effect: dropshadow(...)` approximation.
- Current v1 focuses on a single non-inset shadow. Offsets and radii are adapted to JavaFX parameters. Complex cases may be approximated.

## Warnings and unsupported constructs

To help adoption, the translator inserts inline comments where behavior may differ:

- Use of unsupported shorthands (e.g., an unexpanded `background:`) will receive a warning telling you to use longhands.
- Any leftover per-side border constructs that could not be mapped will receive a warning.
- `-fx-cursor: url(...)` is warned and replaced with a safe fallback.

## Practical tips for authors

- Prefer longhands in the v1 subset; when you use `background:` or `border:` shorthands, ensure they fit the supported patterns.
- For text color, set `color:` in unified CSS; it will become `--fx-text-fill:` on web and `-fx-text-fill:` on JavaFX.
- When you use `border-radius`, you do not need to set a background radius: the CLI duplicates it (`--fx-background-radius` on web, `-fx-background-radius` on JavaFX).
- If you need element-type selectors for JavaFX, use the `fx-` prefix (`fx-label`, `fx-button`, …). On the web, keep using normal HTML selectors.
- If you use `@font-face`, keep them in the unified CSS; the CLI moves them to the top for JavaFX.

## End-to-end example

Unified CSS (input):

```css
:root { --brand: #246; }

fx-label.title {
  color: var(--brand);
  font: 600 14px/1.2 Roboto, sans-serif;
  border: 1px solid #246;
  border-radius: 8px;
  background: linear-gradient(to bottom, #fff, #f7f9ff);
  cursor: pointer;
}
```

Web output (key differences highlighted):

```css
:root { --brand: #246; }

fx-label.title {
  --fx-text-fill: var(--brand);
  font: 600 14px/1.2 Roboto, sans-serif;
  border-radius: 8px;
  --fx-background-radius: 8px;
  background: linear-gradient(to bottom, #fff, #f7f9ff);
  cursor: pointer;
}

fx-label.title:before {
  border: 1px solid #246;
}
```

JavaFX output (translated):

```css
.root { -fx-some-global: something; } /* example of :root → .root when applicable */

Label.title {
  -fx-text-fill: -brand;
  -fx-font-weight: bold;        /* numeric 600 normalized */
  -fx-font-size: 14;            /* px → unitless */
  -fx-font-family: Roboto, sans-serif;
  -fx-border-width: 1px;
  -fx-border-style: solid;
  -fx-border-color: #246;
  -fx-border-radius: 8px;
  -fx-background-radius: 8px;
  -fx-background-image: linear-gradient(180deg, #fff, #f7f9ff);
  -fx-cursor: hand;             /* pointer → hand */
}
```

This reflects the main v1 behaviors. Future versions may broaden the supported subset and reduce approximations.
