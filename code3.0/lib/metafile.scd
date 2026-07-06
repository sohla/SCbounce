// Per-beat audio-derived spectral-occupancy loader (core.audio_meta output).
//
// core.audio_meta emits a JSON object with reference metadata + a flat
// entries array, one entry per row of the source *_beats.txt:
//   {
//     "band_edges_hz": [20,80,160,320,640,1280,2560,5120,10240],
//     "window_seconds": 1.0,
//     "entries": [
//       {"idx":0,"t_start":0.0,"t_end":0.318,"label":"D",
//        "loudness":0.42,"bands":[0.1,0.4,...],"openness":[0.9,0.6,...],
//        "dom_band":3,"centroid_hz":1820.3,"flatness":0.14,"density":0.31},
//       ...
//     ]
//   }
//
// Because the file shares its index space with the beats file that drives
// ~beatClock, no anchor is needed — the lookup is a direct array index.
// This is deliberately kept separate from scorefile.scd:
//   score = MIDI-derived, tick-indexed, hand-anchored via ~scoreAnchorBeat.
//   meta  = audio-derived, beat-indexed, no anchor.
// Different provenance, different failure modes; keep them apart.
//
// Defines:
//   ~loadMeta.(path)          — parses JSON, fills ~metaEntries,
//                                ~metaBandEdges, ~metaWindowSeconds,
//                                ~metaMfccN, ~metaChromaLabels.
//   ~metaAtBeat.(idx)         — ~metaEntries[idx.clip]; returns a Dict.
//   ~openBandAt.(idx)         — index of the currently quietest band.
//   ~bandFreqRange.(bandIdx)  — [loHz, hiHz] for a band, for SynthDef tuning.
//   ~metaReload.()            — re-loads ~metaPath.
//   OSCdef \metaReload        — same, callable via /meta/reload.
//
// Conventions:
//   ~metaPath          path to the JSON (caller sets before ~loadMeta).
//   ~metaEntries       Array of Dicts, one per beats-file row.
//   ~metaBandEdges     Array of Float, length N+1.
//   ~metaWindowSeconds Float, the centered-analysis window used by the
//                        extractor. Diagnostic only.
//   ~metaMfccN         Int, number of MFCC coefficients per entry.
//   ~metaChromaLabels  Array of String, one per pitch class (C..B).

~loadMeta = { |path|
    var raw, entries, edges;
    raw = path.standardizePath.parseYAMLFile;
    if (raw.isNil) {
        Error("could not parse JSON at %".format(path)).throw;
    };
    entries = raw["entries"];
    edges   = raw["band_edges_hz"];
    if (entries.isNil or: { entries.isArray.not }) {
        Error("meta JSON missing 'entries' array (path: %)".format(path)).throw;
    };
    if (edges.isNil or: { edges.isArray.not }) {
        Error("meta JSON missing 'band_edges_hz' array (path: %)".format(path)).throw;
    };
    // parseYAMLFile returns every leaf as a String — coerce with .asFloat /
    // .asInteger at read time (same pattern as scorefile.scd).
    ~metaEntries       = entries;
    ~metaBandEdges     = edges.collect(_.asFloat);
    ~metaWindowSeconds = (raw["window_seconds"] ? "1.0").asFloat;
    ~metaMfccN         = (raw["mfcc_n_coeffs"] ? "13").asInteger;
    ~metaChromaLabels  = (raw["chroma_labels"]
                           ?? { ["C","C#","D","D#","E","F","F#","G","G#","A","A#","B"] })
                        .collect(_.asString);
    "loaded % meta entries, % bands, % mfcc, % chroma, window=%s from %".format(
        entries.size, ~metaBandEdges.size - 1, ~metaMfccN,
        ~metaChromaLabels.size, ~metaWindowSeconds, path).postln;
    entries.size;
};

~metaAtBeat = { |beatIdx|
    var i = beatIdx.clip(0, ~metaEntries.size - 1);
    ~metaEntries[i];
};

// Return the band index whose openness is highest at this beat — i.e. the
// band with the most spectral room for a synth. Ties broken by lowest index.
~openBandAt = { |beatIdx|
    var m = ~metaAtBeat.(beatIdx);
    var o = m["openness"].collect(_.asFloat);
    o.maxIndex;
};

// (loHz, hiHz) for a band index. Handy for constraining a SynthDef's
// fundamental / filter cutoff to the spectral gap.
~bandFreqRange = { |bandIdx|
    var i = bandIdx.clip(0, ~metaBandEdges.size - 2);
    [~metaBandEdges[i], ~metaBandEdges[i + 1]];
};

~metaReload = {
    if (~metaPath.isNil) {
        "metaReload: ~metaPath not set".warn;
    } {
        ~loadMeta.(~metaPath);
        "meta reloaded from %".format(~metaPath).postln;
    };
};

OSCdef(\metaReload, { ~metaReload.value }, '/meta/reload');
