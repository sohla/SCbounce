// Shared beats-file loader. Mirrors the core/beatfile.py contract:
//   start_time<TAB>end_time<TAB>label   where label is "D" or "b".
// Times in seconds, strictly increasing on start_time.
//
// Defines one top-level function:
//   ~loadBeats.(path)       — populates ~beats, ~ends, ~labels; returns count.
//
// Repo-root resolution is each .scd file's responsibility — the canonical
// idiom is `thisProcess.nowExecutingPath.dirname.dirname` (operates on the
// file path directly, no PathName trailing-slash dependency).

~loadBeats = { |path|
    var file, text, beats, labels, ends;
    file = File(path.standardizePath, "r");
    if (file.isOpen.not) { Error("could not open beats file: %".format(path)).throw };
    text = file.readAllString;
    file.close;

    beats  = List.new;
    labels = List.new;
    ends   = List.new;

    text.split($\n).do { |raw|
        var line = raw.stripWhiteSpace;
        var parts;
        if (line.notEmpty) {
            parts = line.split($\t);
            if (parts.size != 3) {
                Error("bad line (expected 3 tab-separated columns): %".format(line)).throw;
            };
            beats.add(parts[0].asFloat);
            ends.add(parts[1].asFloat);
            labels.add(parts[2].asSymbol);  // \D or \b
        };
    };

    ~beats  = beats.asArray;
    ~ends   = ends.asArray;
    ~labels = labels.asArray;

    "loaded % beats from %".format(~beats.size, path).postln;
    ~beats.size;
};
