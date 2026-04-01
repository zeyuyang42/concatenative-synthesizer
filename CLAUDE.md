# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Project Is

A GUI-based concatenative sound synthesizer built in SuperCollider. It uses the `Concat2` extension to match audio frames from a control input against a source audio database, enabling real-time feature-matched playback. Intended for glitch and ambient music creation.

## Running the Synthesizer

This is a single-file SuperCollider application (`CSS.sc`). There is no build system, package manager, or test suite.

**Step 1 — Boot the server** (run these 4 lines first in the SuperCollider IDE):
```supercollider
s.boot；
s.options.sampleRate = 44100;
s.options.memSize_(65536 * 4);
s.reboot；
```

**Step 2 — Run the synthesizer**: Select and evaluate the entire `( ... )` block in `CSS.sc`. A GUI window will appear.

Requirements: SuperCollider 3.12.1+, macOS (not tested on Linux/Windows). The `Concat2` and `JPverb` extensions must be installed.

## Architecture

All code lives in `CSS.sc`. The file has two top-level sections:

1. **Server setup** (lines 1–4): Boot commands and options — these must be run separately before the main block.
2. **Main block** (lines 5–end): One large `( ... )` block with `s.waitForBoot { ... }` containing everything else.

### Audio Signal Chain

```
[Control Input] → Bus 84 ──┐
                            ├→ zy_concate (Concat2) → Bus 42 → zy_10eq → Bus 51 → zy_reverb → Bus 30 → zy_output → Bus 0
[Source Input]  → Bus 73 ──┘
```

Named audio buses (hardcoded integers):
- `84` — control signal
- `73` — source signal
- `42` — concatenative synthesis output
- `51` — EQ output
- `30` — reverb output
- `0` — final stereo output
- `99` — silence

### Synthesis Units (SynthDefs)

- `zy_control` / `zy_source` — Input signal generators using `Ndef` (named synth nodes); support file playback via `PlayBuf`/`Warp1` or live microphone input via `SoundIn`
- `zy_concate` — Core Concat2 engine; parameters: ZCR/loudness/spectral centroid/spectral tilt feature weights, seek time, match duration, store size, freeze toggle
- `zy_10eq` — 10-band parametric EQ using `BPeakEQ` at fixed center frequencies (36 Hz to 18 kHz)
- `zy_reverb` — JPverb reverb with t60, damping, size, diffusion, modulation depth/frequency, low/high shelf, and wet/dry mix
- `zy_output` — Final output gain, monitoring, and recording via `RecordBuf`

### Buffers

- `buf_ctrl` — Loaded from `demo_control.wav` (default control signal)
- `buf_src` — Loaded from `demo_source.wav` (default source signal)
- `buf_rcd` — 10-second recording buffer (44100 Hz, mono)
- Three 1024-sample scope buffers for oscilloscope views

### GUI Layout

The GUI uses SuperCollider's Swing framework. Panels are arranged in a main `HLayout`:
1. **Scope** — Three oscilloscope views (control, source, output) + mascot image (`marvin6.png`)
2. **Inputs** — File loading with time-stretch, code evaluation (`Ndef`), live audio toggle
3. **Concatenative** — Feature weight knobs (ZCR, loudness, SC, ST) + temporal knobs + freeze/reset
4. **EQ** — Sliders for 10 band frequencies and bandwidths
5. **Reverb** — Knobs for all JPverb parameters + wet/dry slider
6. **Utilities** — Record/play/stream buttons, gain knobs for output/control/source

The `recordings/` directory is where audio files are saved when using the record feature.
