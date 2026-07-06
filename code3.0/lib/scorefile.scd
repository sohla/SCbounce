// Per-bar musical-parameter score loader (PyTrackMusic's tick_index.json).
//
// PyTrackMusic emits a flat JSON array, one entry per bar, keyed by MIDI tick:
//   [{"tick":0,"bar":1,"chord_root":"A","tension":0.0,"fitted_notes":[[69],[69]],...}, ...]
//
// Lookup keys the bar containing a given tick. To go from SC's beat counter
// (~beats index from beatfile.scd) to a tick:
//   tick = (beatIndex + ~scoreAnchorBeat) * ~scoreTicksPerBeat
//
// Anchor offset is tuned by ear against the recording; see sync_plan2_score.scd.
// Reload mechanism (`/score/reload` OSCdef) lets a future web editor mutate the
// JSON on disk and trigger SC to re-parse without restarting playback.
//
// Defines:
//   ~loadScore.(path)        — parses JSON, fills ~scoreEntries, ~scoreTicks.
//   ~paramsAtTick.(tick)     — bar entry whose [tick, next_tick) contains `tick`.
//   ~paramsAtBeat.(beat)     — convenience: applies anchor + tpb conversion.
//   ~scoreReload.()          — re-loads ~scorePath.
//   OSCdef \scoreReload      — same, callable via /score/reload.
//
// Conventions:
//   ~scorePath              path to the JSON (caller sets before ~loadScore).
//   ~scoreTicksPerBeat      MIDI ticks per *true* beat (480 for Beethoven 7 mvt 4).
//   ~scoreEventsPerBeat     events in the beats.txt file per true beat. 1 for
//                           the unsubdivided file (...beats.txt or ..._2.txt);
//                           2 for _mul2; 4 for _mul4. Matters because the
//                           caller indexes ~paramsAtBeat by the beats-file row,
//                           not by a true beat number.
//   ~scoreAnchorBeat        offset in *true beats*, tunable live by ear.

~scoreTicksPerBeat  = ~scoreTicksPerBeat  ? 480;
~scoreEventsPerBeat = ~scoreEventsPerBeat ? 1;
~scoreAnchorBeat    = ~scoreAnchorBeat    ? 0;

~loadScore = { |path|
    var raw, entries, ticks;
    raw = path.standardizePath.parseYAMLFile;
    if (raw.isNil or: { raw.isArray.not }) {
        Error("could not parse JSON array at %".format(path)).throw;
    };
    // parseYAMLFile returns every leaf as a String. Pre-convert tick to Int so
    // indexOfGreaterThan compares numerically, not lexicographically.
    entries = raw;
    ticks = entries.collect({ |e| e["tick"].asInteger });
    ~scoreEntries = entries;
    ~scoreTicks   = ticks;
    "loaded % score entries from %".format(entries.size, path).postln;
    entries.size;
};

~paramsAtTick = { |t|
    // indexOfGreaterThan returns the first index whose tick > t. The entry we
    // want is the one immediately before that — i.e. the bar whose [tick, next)
    // half-open range contains t. If t is past the last tick, return the last
    // entry. If t is below 0, return the first.
    var i = ~scoreTicks.indexOfGreaterThan(t) ? ~scoreTicks.size;
    ~scoreEntries[(i - 1).max(0)];
};

// Convert a beats.txt row index to a MIDI tick, accounting for the file's
// subdivision factor. Anchor is in true beats so anchor=±1 always nudges by one
// musical beat regardless of subdivision.
~paramsAtBeat = { |beatIdx|
    var tick = ((beatIdx / ~scoreEventsPerBeat) + ~scoreAnchorBeat) * ~scoreTicksPerBeat;
    ~paramsAtTick.(tick);
};

~scoreReload = {
    if (~scorePath.isNil) {
        "scoreReload: ~scorePath not set".warn;
    } {
        ~loadScore.(~scorePath);
        "score reloaded from %".format(~scorePath).postln;
    };
};

OSCdef(\scoreReload, { ~scoreReload.value }, '/score/reload');
