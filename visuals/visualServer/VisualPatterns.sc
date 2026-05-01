// Visual Pattern Classes - modeled after SuperCollider's pattern system

Vbind : Pbind {
    *new { |...pairs|
        var pattern = super.new;
        var defaults = (type: \visual, dur: 1, instrument: \circle, view: \default);
        
        // Merge defaults with provided pairs
        var allPairs = [defaults.asPairs, pairs].flat;
        
        ^pattern.putPairs(allPairs);
    }
}

Vpulse : Pbind {
    *new { |...pairs|
        var pattern = super.new;
        var defaults = (type: \vpulse, dur: 1, view: \default);
        
        var allPairs = [defaults.asPairs, pairs].flat;
        
        ^pattern.putPairs(allPairs);
    }
}

Vline : Pbind {
    *new { |...pairs|
        var pattern = super.new;
        var defaults = (type: \vline, dur: 1, view: \default);
        
        var allPairs = [defaults.asPairs, pairs].flat;
        
        ^pattern.putPairs(allPairs);
    }
}

Vlive : Pbind {
    *new { |...pairs|
        var pattern = super.new;
        var defaults = (type: \vlive, dur: inf, view: \default);
        
        var allPairs = [defaults.asPairs, pairs].flat;
        
        ^pattern.putPairs(allPairs);
    }
}

// Audio-Visual combined pattern
AVbind : Pbind {
    *new { |...pairs|
        var pattern = super.new;
        var defaults = (type: \audioVisual, dur: 1, instrument: \default, view: \default);
        
        var allPairs = [defaults.asPairs, pairs].flat;
        
        ^pattern.putPairs(allPairs);
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

// Live input stream pattern
LiveParam : Pattern {
    var <func, <dur;
    
    *new { |func, dur = 0.1|
        ^super.newCopyArgs(func, dur);
    }
    
    asStream {
        ^Routine({
            loop {
                this.func.value.yield;
                this.dur.wait;
            };
        });
    }
    
    embedInStream { |inval|
        var stream = this.asStream;
        var val;
        while { 
            val = stream.next(inval);
            val.notNil;
        } {
            inval = val.yield;
        };
        ^inval;
    }
}

// Visual pattern player - manages playback of visual patterns
VpatternPlayer {
    var <pattern, <clock, <player, <view, <server;
    
    *new { |pattern, clock, view = \default, server|
        ^super.newCopyArgs(pattern, clock ?? TempoClock.default, view, server ?? VisualServer.default);
    }
    
    play { |quant|
        // Ensure view exists
        if (this.server.views[this.view].isNil) {
            this.server.createView(this.view);
        };
        
        // Start pattern
        this.player = EventStreamPlayer(this.pattern.asStream, this.clock);
        this.player.play(quant);
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