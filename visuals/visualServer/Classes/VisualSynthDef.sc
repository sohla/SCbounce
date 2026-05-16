VisualSynthDef {
    classvar <all;
    var <name, <renderFunc, <defaultParams;

    *initClass {
        all = Dictionary.new;
        // Register built-ins here, after `all` exists and where nothing
        // resets it afterwards. (Previously VisualServer.initClass populated
        // this and VisualSynthDef.initClass then wiped it - initClass order
        // is not guaranteed by a runtime call inside VisualServer:init.)
        this.initBuiltInDefs;
    }

    *new { |name, renderFunc, defaultParams|
        ^super.newCopyArgs(name, renderFunc, defaultParams ?? Dictionary.new).init;
    }

    *at { |name|
        if (all.isNil) { ^nil };
        ^all[name];
    }

    init {
        if (all.isNil) { all = Dictionary.new };
        all.put(name, this);
    }

    // Helper method to get parameter with default.
    // Function-valued params are evaluated every call (live input). Pattern
    // streams are already resolved to concrete values before reaching here.
    getParam { |node, key, default|
        var val = node[\params][key] ?? defaultParams[key] ?? default;
        ^if (val.isFunction) { val.value } { val };
    }

    // Strictly-numeric param lookup for envelope timing. SC's default parent
    // event injects machinery into played events (e.g. sustain as the
    // Function #{ ~dur*~legato*~stretch }); evaluating that here would crash
    // (nil * nil). Timing values are always plain numbers, so anything that
    // isn't a number falls back to `default`.
    numParam { |node, key, default|
        var v = node[\params][key];
        ^if (v.isNumber) { v } { default };
    }

    // Helper to calculate envelope value
    calcEnv { |elapsed, duration, curve = \linear|
        var phase = (elapsed / duration).clip(0, 1);
        ^switch(curve,
            \linear, { phase },
            \sin, { sin(phase * pi * 0.5) },
            \cos, { 1 - cos(phase * pi * 0.5) },
            \exp, { phase.squared },
            \log, { phase.sqrt },
            { phase }
        );
    }

    // Map a curve symbol to an Env-safe curve. Env exp segments cannot touch
    // level 0 (our levels include 0), so \exp/\log become numeric curves.
    envCurve { |sym|
        ^switch(sym,
            \linear, { \lin },
            \lin,    { \lin },
            \sin,    { \sin },
            \cos,    { \sin },
            \exp,    { -4 },
            \log,    { 4 },
            { \sin }
        );
    }

    // Per-frame envelope state for a node. Returns an Event
    // (alpha:, scale:, expired:, life:). Lifetime is decoupled from \dur:
    //   life = (\sustain ? \dur) * \legato * \stretch
    // \legato/\stretch scale only the total life (the overlap control).
    // \attack and \release are ABSOLUTE seconds (NOT scaled), clamped into
    // life; the remainder is the hold. This matches SC's note model (legato
    // scales hold; Env attack/release segment lengths are fixed seconds) and
    // keeps \release usable at any \legato. Defaults (legato/stretch 1,
    // attack 0, release nil, sustain nil) => fade 1->0 over \dur, free at
    // \dur (original behaviour). life == inf => persistent (never expires).
    envState { |node, elapsed|
        var ts, base, life, atk, rel0, rel, hold, crv, env, v;

        ts   = this.numParam(node, \legato, 1) * this.numParam(node, \stretch, 1);
        base = this.numParam(node, \sustain, this.numParam(node, \dur, 1));
        life = base * ts;
        if (life == inf) { ^(alpha: 1, scale: 1, expired: false, life: inf) };

        atk  = this.numParam(node, \attack, 0).clip(0, life);
        rel0 = this.numParam(node, \release, nil);
        rel  = if (rel0.notNil) { rel0.clip(0, life - atk) } { life - atk };
        hold = (life - atk - rel).max(0);
        crv  = this.envCurve(this.getParam(node, \curve, \sin));

        env = Env([0, 1, 1, 0], [atk, hold, rel], crv);
        v   = env.at(elapsed.clip(0, life));
        ^(alpha: v, scale: v, expired: elapsed > life, life: life);
    }
}

+ VisualSynthDef {
    *initBuiltInDefs {
        // Lifetime/envelope is centralised in VisualSynthDef.envState:
        // st = (alpha:, scale:, expired:, life:). A `^` in these closures
        // would be an out-of-context return (they outlive initBuiltInDefs),
        // so expiry is handled with if (expired) { free } { draw }.

        VisualSynthDef(\circle, { |node, view, elapsed, centerX, centerY, params|
            var def, st, x, y, size, color, fill;

            def = node[\def];
            st  = def.envState(node, elapsed);
            if (st[\expired]) {
                VisualServer.default.vfree(node[\nodeID]);
            } {
                x = def.getParam(node, \x, 0) * centerX + centerX;
                y = def.getParam(node, \y, 0) * centerY + centerY;
                size = def.getParam(node, \size, 50) * st[\scale];
                color = def.getParam(node, \color, Color.white).copy.alpha_(st[\alpha]);
                fill = def.getParam(node, \fill, true);

                Pen.fillColor = color;
                Pen.strokeColor = color;
                Pen.addOval(Rect(x - (size/2), y - (size/2), size, size));
                if (fill) { Pen.fill } { Pen.stroke };
            };
        }, (x: 0, y: 0, size: 50, color: Color.white, dur: 1, fill: true));

        VisualSynthDef(\square, { |node, view, elapsed, centerX, centerY, params|
            var def, st, x, y, size, color, fill;

            def = node[\def];
            st  = def.envState(node, elapsed);
            if (st[\expired]) {
                VisualServer.default.vfree(node[\nodeID]);
            } {
                x = def.getParam(node, \x, 0) * centerX + centerX;
                y = def.getParam(node, \y, 0) * centerY + centerY;
                size = def.getParam(node, \size, 50) * st[\scale];
                color = def.getParam(node, \color, Color.white).copy.alpha_(st[\alpha]);
                fill = def.getParam(node, \fill, true);

                Pen.fillColor = color;
                Pen.strokeColor = color;
                Pen.addRect(Rect(x - (size/2), y - (size/2), size, size));
                if (fill) { Pen.fill } { Pen.stroke };
            };
        }, (x: 0, y: 0, size: 50, color: Color.white, dur: 1, fill: true));

        VisualSynthDef(\line, { |node, view, elapsed, centerX, centerY, params|
            var def, st, x1, y1, x2, y2, color, width;

            def = node[\def];
            st  = def.envState(node, elapsed);
            if (st[\expired]) {
                VisualServer.default.vfree(node[\nodeID]);
            } {
                x1 = def.getParam(node, \x1, -0.5) * centerX + centerX;
                y1 = def.getParam(node, \y1, 0) * centerY + centerY;
                x2 = def.getParam(node, \x2, 0.5) * centerX + centerX;
                y2 = def.getParam(node, \y2, 0) * centerY + centerY;
                color = def.getParam(node, \color, Color.white).copy.alpha_(st[\alpha]);
                width = def.getParam(node, \width, 1) * st[\scale];

                Pen.strokeColor = color;
                Pen.width = width;
                Pen.moveTo(x1 @ y1);
                Pen.lineTo(x2 @ y2);
                Pen.stroke;
            };
        }, (x1: -0.5, y1: 0, x2: 0.5, y2: 0, color: Color.white, width: 1, dur: 1));

        // Animated circle: own startSize->endSize sweep (over the node life),
        // alpha/expiry from envState (scale NOT applied - size is the sweep).
        VisualSynthDef(\pulse, { |node, view, elapsed, centerX, centerY, params|
            var def, st, x, y, startSize, endSize, color, curve, phase, size;

            def = node[\def];
            st  = def.envState(node, elapsed);
            if (st[\expired]) {
                VisualServer.default.vfree(node[\nodeID]);
            } {
                x = def.getParam(node, \x, 0) * centerX + centerX;
                y = def.getParam(node, \y, 0) * centerY + centerY;
                startSize = def.getParam(node, \startSize, 10);
                endSize = def.getParam(node, \endSize, 100);
                curve = def.getParam(node, \curve, \exp);
                phase = def.calcEnv(elapsed, st[\life], curve);
                size = startSize.blend(endSize, phase);
                color = def.getParam(node, \color, Color.white).copy.alpha_(st[\alpha]);

                Pen.fillColor = color;
                Pen.addOval(Rect(x - (size/2), y - (size/2), size, size));
                Pen.fill;
            };
        }, (x: 0, y: 0, startSize: 10, endSize: 100, color: Color.white, dur: 1, curve: \exp));

        // Spinning line. Persistent by default (dur: inf -> never expires).
        VisualSynthDef(\spinner, { |node, view, elapsed, centerX, centerY, params|
            var def, st, x, y, length, color, speed, width, angle, x1, y1, x2, y2;

            def = node[\def];
            st  = def.envState(node, elapsed);
            if (st[\expired]) {
                VisualServer.default.vfree(node[\nodeID]);
            } {
                x = def.getParam(node, \x, 0) * centerX + centerX;
                y = def.getParam(node, \y, 0) * centerY + centerY;
                length = def.getParam(node, \length, 50) * st[\scale];
                speed = def.getParam(node, \speed, 1);
                width = def.getParam(node, \width, 2);
                color = def.getParam(node, \color, Color.white).copy.alpha_(st[\alpha]);

                angle = elapsed * speed * 2pi;
                x1 = x + (cos(angle) * length / 2);
                y1 = y + (sin(angle) * length / 2);
                x2 = x - (cos(angle) * length / 2);
                y2 = y - (sin(angle) * length / 2);

                Pen.strokeColor = color;
                Pen.width = width;
                Pen.moveTo(x1 @ y1);
                Pen.lineTo(x2 @ y2);
                Pen.stroke;
            };
        }, (x: 0, y: 0, length: 50, color: Color.white, speed: 1, dur: inf, width: 2));

        // Live parameter circle. Function params are evaluated by getParam.
        // Persistent by default (dur: inf).
        VisualSynthDef(\live, { |node, view, elapsed, centerX, centerY, params|
            var def, st, x, y, size, color, fill;

            def = node[\def];
            st  = def.envState(node, elapsed);
            if (st[\expired]) {
                VisualServer.default.vfree(node[\nodeID]);
            } {
                x = def.getParam(node, \x, 0) * centerX + centerX;
                y = def.getParam(node, \y, 0) * centerY + centerY;
                size = def.getParam(node, \size, 50) * st[\scale];
                color = def.getParam(node, \color, Color.white).copy.alpha_(st[\alpha]);
                fill = def.getParam(node, \fill, true);

                Pen.fillColor = color;
                Pen.strokeColor = color;
                Pen.addOval(Rect(x - (size/2), y - (size/2), size, size));
                if (fill) { Pen.fill } { Pen.stroke };
            };
        }, (x: 0, y: 0, size: 50, color: Color.white, dur: inf, fill: true));
    }
}

// Built-in defs are registered in VisualSynthDef.initClass (see above).