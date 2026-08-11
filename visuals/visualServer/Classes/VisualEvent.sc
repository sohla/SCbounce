// Helper class for creating visual events from patterns
// Event types are auto-registered by VisualEventTypes:*initClass at compile
VisualEvent {
    *new { |type = \visual, args|
        var event = Event.new;
        event[\type] = type;
        if (args.notNil) {
            args.keysValuesDo { |key, value|
                event[key] = value;
            };
        };
        ^event;
    }
    
    *circle { |x = 0, y = 0, size = 50, color = nil, dur = 1, view = \default|
        ^this.new(\visual, (
            instrument: \circle,
            x: x, y: y, size: size,
            color: color ?? Color.white,
            dur: dur, view: view
        ));
    }
    
    *pulse { |x = 0, y = 0, startSize = 10, endSize = 100, color = nil, dur = 1, view = \default|
        ^this.new(\vpulse, (
            x: x, y: y,
            startSize: startSize, endSize: endSize,
            color: color ?? Color.white,
            dur: dur, view: view
        ));
    }
    
    *line { |x1 = -0.5, y1 = 0, x2 = 0.5, y2 = 0, color = nil, width = 1, dur = 1, view = \default|
        ^this.new(\vline, (
            x1: x1, y1: y1, x2: x2, y2: y2,
            color: color ?? Color.white,
            width: width, dur: dur, view: view
        ));
    }
    
    *live { |x = 0, y = 0, size = 50, color = nil, dur = inf, view = \default|
        ^this.new(\vlive, (
            x: x, y: y, size: size,
            color: color ?? Color.white,
            dur: dur, view: view
        ));
    }
}