// Visual Pattern Classes - modeled after SuperCollider's pattern system

// These build a real Pbind. Defaults come first so user-supplied pairs
// (appended after) override them when Pbind constructs the event.

Vbind {
    *new { |...pairs|
        var defaults = (type: \visual, dur: 1, instrument: \circle, view: \default);
        ^Pbind(*[defaults.asPairs, pairs].flatten(1));
    }
}

Vpulse {
    *new { |...pairs|
        var defaults = (type: \vpulse, dur: 1, view: \default);
        ^Pbind(*[defaults.asPairs, pairs].flatten(1));
    }
}

Vline {
    *new { |...pairs|
        var defaults = (type: \vline, dur: 1, view: \default);
        ^Pbind(*[defaults.asPairs, pairs].flatten(1));
    }
}

Vlive {
    *new { |...pairs|
        var defaults = (type: \vlive, dur: inf, view: \default);
        ^Pbind(*[defaults.asPairs, pairs].flatten(1));
    }
}

// Audio-Visual combined pattern
AVbind {
    *new { |...pairs|
        var defaults = (type: \audioVisual, dur: 1, instrument: \default, view: \default);
        ^Pbind(*[defaults.asPairs, pairs].flatten(1));
    }
}

// Visual Sequence - like Pseq but for visual events
Vseq {
    *new { |list, repeats = 1, offset = 0, view = \default|
        ^Pseq(
            list.collect { |item|
                if (item.isKindOf(Dictionary) || item.isKindOf(Event)) {
                    item[\type] = item[\type] ?? \visual;
                    item[\view] = item[\view] ?? view;
                    item;
                } {
                    (type: \visual, instrument: item, view: view);
                };
            },
            repeats, offset
        );
    }
}

// Visual parallel - play multiple visual patterns simultaneously
Vpar {
    *new { |list, repeats = 1|
        ^Ppar(list, repeats);
    }
}

// Visual random choice
Vrand {
    *new { |list, repeats = inf, view = \default|
        ^Prand(
            list.collect { |item|
                if (item.isKindOf(Dictionary) || item.isKindOf(Event)) {
                    item[\type] = item[\type] ?? \visual;
                    item[\view] = item[\view] ?? view;
                    item;
                } {
                    (type: \visual, instrument: item, view: view);
                };
            },
            repeats
        );
    }
}

// Visual weighted random choice
Vwrand {
    *new { |list, weights, repeats = inf, view = \default|
        ^Pwrand(
            list.collect { |item|
                if (item.isKindOf(Dictionary) || item.isKindOf(Event)) {
                    item[\type] = item[\type] ?? \visual;
                    item[\view] = item[\view] ?? view;
                    item;
                } {
                    (type: \visual, instrument: item, view: view);
                };
            },
            weights, repeats
        );
    }
}

// Live input value pattern: evaluates `func` once per event.
// Timing is controlled by the host Pbind's \dur, NOT internally - a wait
// here would be yielded as a parameter value and corrupt the stream.
// Extra *new args (lag, dur, ...) are accepted and ignored for back-compat.
LiveParam : Pattern {
    var <func;

    *new { |func ... ignored|
        ^super.newCopyArgs(func);
    }

    embedInStream { |inval|
        loop { inval = func.value(inval).yield };
        ^inval;
    }
}

// Alias used by the examples
LiveStream : LiveParam {}

// Visual pattern player - manages playback of visual patterns
VpatternPlayer {
    // Order matters: newCopyArgs assigns positionally in declaration order.
    // `player` must be last so the 4 *new args map to pattern/clock/view/server.
    var <pattern, <clock, <view, <server, <>player;

    *new { |pattern, clock, view = \default, server|
        ^super.newCopyArgs(pattern, clock ? TempoClock.default, view, server ? VisualServer.default);
    }
    
    play { |quant|
        // No view pre-creation: each event carries its own \view and
        // VisualServer:vnew auto-creates that view on demand. Pre-creating
        // this.view here would spawn a spurious empty \default window.
        //
        // Pattern:play(clock, protoEvent, quant) builds and plays the
        // EventStreamPlayer correctly (its 2nd arg is the protoEvent, not a clock).
        player = this.pattern.play(this.clock, nil, quant);
        ^this;
    }
    
    stop {
        if (this.player.notNil) {
            this.player.stop;
            this.player = nil;
        };
        ^this;
    }
    
    pause {
        if (this.player.notNil) {
            this.player.pause;
        };
        ^this;
    }
    
    resume {
        if (this.player.notNil) {
            this.player.resume;
        };
        ^this;
    }
    
    isPlaying {
        ^this.player.notNil && this.player.isPlaying;
    }
}

// Convenience methods for Pattern class
+ Pattern {
    vplay { |clock, view = \default, server, quant|
        ^VpatternPlayer(this, clock, view, server ?? VisualServer.default).play(quant);
    }
}