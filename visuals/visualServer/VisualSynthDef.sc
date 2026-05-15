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
}

+ VisualSynthDef {
    *initBuiltInDefs {
        // Basic shape definitions
        VisualSynthDef(\circle, { |node, view, elapsed, centerX, centerY, params|
            var def, x, y, size, color, duration, fill;
            var alpha, phase;
            
            def = node[\def];
            x = def.getParam(node, \x, 0) * centerX + centerX;
            y = def.getParam(node, \y, 0) * centerY + centerY;
            size = def.getParam(node, \size, 50);
            color = def.getParam(node, \color, Color.white);
            duration = def.getParam(node, \dur, 1);
            fill = def.getParam(node, \fill, true);

            // Envelope for alpha
            alpha = 1;
            if (duration != inf) {
                phase = def.calcEnv(elapsed, duration, \sin);
                alpha = 1 - phase;
            };

            if ((duration != inf) and: { elapsed > duration }) {
                // Auto-free expired node, then draw nothing. A `^` here would
                // be an out-of-context return: this closure outlives the
                // initBuiltInDefs method that defined it.
                VisualServer.default.vfree(node[\nodeID]);
            } {
                color = color.copy.alpha_(alpha);
                Pen.fillColor = color;
                Pen.strokeColor = color;

                if (fill) {
                    Pen.addOval(Rect(x - size/2, y - size/2, size, size));
                    Pen.fill;
                } {
                    Pen.addOval(Rect(x - size/2, y - size/2, size, size));
                    Pen.stroke;
                };
            };
        }, (x: 0, y: 0, size: 50, color: Color.white, dur: 1, fill: true));

        VisualSynthDef(\square, { |node, view, elapsed, centerX, centerY, params|
            var def, x, y, size, color, duration, fill;
            var alpha, phase;
            
            def = node[\def];
            x = def.getParam(node, \x, 0) * centerX + centerX;
            y = def.getParam(node, \y, 0) * centerY + centerY;
            size = def.getParam(node, \size, 50);
            color = def.getParam(node, \color, Color.white);
            duration = def.getParam(node, \dur, 1);
            fill = def.getParam(node, \fill, true);

            alpha = 1;
            if (duration != inf) {
                phase = def.calcEnv(elapsed, duration, \sin);
                alpha = 1 - phase;
            };

            if ((duration != inf) and: { elapsed > duration }) {
                VisualServer.default.vfree(node[\nodeID]);
            } {
                color = color.copy.alpha_(alpha);
                Pen.fillColor = color;
                Pen.strokeColor = color;

                if (fill) {
                    Pen.addRect(Rect(x - size/2, y - size/2, size, size));
                    Pen.fill;
                } {
                    Pen.addRect(Rect(x - size/2, y - size/2, size, size));
                    Pen.stroke;
                };
            };
        }, (x: 0, y: 0, size: 50, color: Color.white, dur: 1, fill: true));

        VisualSynthDef(\line, { |node, view, elapsed, centerX, centerY, params|
            var def, x1, y1, x2, y2, color, width, duration;
            var alpha, phase;
            
            def = node[\def];
            x1 = def.getParam(node, \x1, -0.5) * centerX + centerX;
            y1 = def.getParam(node, \y1, 0) * centerY + centerY;
            x2 = def.getParam(node, \x2, 0.5) * centerX + centerX;
            y2 = def.getParam(node, \y2, 0) * centerY + centerY;
            color = def.getParam(node, \color, Color.white);
            width = def.getParam(node, \width, 1);
            duration = def.getParam(node, \dur, 1);

            alpha = 1;
            if (duration != inf) {
                phase = def.calcEnv(elapsed, duration, \sin);
                alpha = 1 - phase;
            };

            if ((duration != inf) and: { elapsed > duration }) {
                VisualServer.default.vfree(node[\nodeID]);
            } {
                Pen.strokeColor = color.copy.alpha_(alpha);
                Pen.width = width;
                Pen.moveTo(x1 @ y1);
                Pen.lineTo(x2 @ y2);
                Pen.stroke;
            };
        }, (x1: -0.5, y1: 0, x2: 0.5, y2: 0, color: Color.white, width: 1, dur: 1));

        // Animated circle with size envelope
        VisualSynthDef(\pulse, { |node, view, elapsed, centerX, centerY, params|
            var def, x, y, startSize, endSize, color, duration, curve;
            var phase, size, alpha;
            
            def = node[\def];
            x = def.getParam(node, \x, 0) * centerX + centerX;
            y = def.getParam(node, \y, 0) * centerY + centerY;
            startSize = def.getParam(node, \startSize, 10);
            endSize = def.getParam(node, \endSize, 100);
            color = def.getParam(node, \color, Color.white);
            duration = def.getParam(node, \dur, 1);
            curve = def.getParam(node, \curve, \exp);

            phase = def.calcEnv(elapsed, duration, curve);
            size = startSize.blend(endSize, phase);
            alpha = 1 - phase;

            if ((duration != inf) and: { elapsed > duration }) {
                VisualServer.default.vfree(node[\nodeID]);
            } {
                Pen.fillColor = color.copy.alpha_(alpha);
                Pen.addOval(Rect(x - size/2, y - size/2, size, size));
                Pen.fill;
            };
        }, (x: 0, y: 0, startSize: 10, endSize: 100, color: Color.white, dur: 1, curve: \exp));

        // Spinning line
        VisualSynthDef(\spinner, { |node, view, elapsed, centerX, centerY, params|
            var def, x, y, length, color, speed, duration, width;
            var alpha, phase, angle, x1, y1, x2, y2;
            
            def = node[\def];
            x = def.getParam(node, \x, 0) * centerX + centerX;
            y = def.getParam(node, \y, 0) * centerY + centerY;
            length = def.getParam(node, \length, 50);
            color = def.getParam(node, \color, Color.white);
            speed = def.getParam(node, \speed, 1);
            duration = def.getParam(node, \dur, inf);
            width = def.getParam(node, \width, 2);

            alpha = 1;
            if (duration != inf) {
                phase = def.calcEnv(elapsed, duration, \linear);
                alpha = 1 - phase;
            };

            if ((duration != inf) and: { elapsed > duration }) {
                VisualServer.default.vfree(node[\nodeID]);
            } {
                angle = elapsed * speed * 2pi;
                x1 = x + (cos(angle) * length/2);
                y1 = y + (sin(angle) * length/2);
                x2 = x - (cos(angle) * length/2);
                y2 = y - (sin(angle) * length/2);

                Pen.strokeColor = color.copy.alpha_(alpha);
                Pen.width = width;
                Pen.moveTo(x1 @ y1);
                Pen.lineTo(x2 @ y2);
                Pen.stroke;
            };
        }, (x: 0, y: 0, length: 50, color: Color.white, speed: 1, dur: inf, width: 2));

        // Live parameter circle (for real-time input)
        VisualSynthDef(\live, { |node, view, elapsed, centerX, centerY, params|
            var def, x, y, size, color;
            var xParam, yParam, sizeParam, colorParam, duration, fill, alpha;
            
            def = node[\def];
            
            // Check for live parameter functions
            xParam = def.getParam(node, \x, 0);
            yParam = def.getParam(node, \y, 0);
            sizeParam = def.getParam(node, \size, 50);
            colorParam = def.getParam(node, \color, Color.white);
            
            // Evaluate functions if they are functions, otherwise use as values
            x = if (xParam.isFunction) { xParam.value } { xParam };
            y = if (yParam.isFunction) { yParam.value } { yParam };
            size = if (sizeParam.isFunction) { sizeParam.value } { sizeParam };
            color = if (colorParam.isFunction) { colorParam.value } { colorParam };
            
            x = x * centerX + centerX;
            y = y * centerY + centerY;

            duration = def.getParam(node, \dur, inf);
            fill = def.getParam(node, \fill, true);
            
            alpha = 1;
            if ((duration != inf) and: { elapsed > duration }) {
                VisualServer.default.vfree(node[\nodeID]);
            } {
                color = color.copy.alpha_(alpha);
                Pen.fillColor = color;
                Pen.strokeColor = color;

                if (fill) {
                    Pen.addOval(Rect(x - size/2, y - size/2, size, size));
                    Pen.fill;
                } {
                    Pen.addOval(Rect(x - size/2, y - size/2, size, size));
                    Pen.stroke;
                };
            };
        }, (x: 0, y: 0, size: 50, color: Color.white, dur: inf, fill: true));
    }
}

// Built-in defs are registered in VisualSynthDef.initClass (see above).