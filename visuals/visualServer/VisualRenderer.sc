VisualRenderer {
    classvar <default;
    var <>server, <>views, <>isRunning;

    *initClass {
        default = VisualRenderer.new;
    }

    *new { |server|
        ^super.newCopyArgs(server ?? VisualServer.default).init;
    }

    init {
        this.server = server ?? VisualServer.default;
        this.views = Dictionary.new;
        this.isRunning = false;
    }

    start {
        if (this.isRunning.not) {
            this.isRunning = true;
            this.server.start;
            ("Visual renderer started").postln;
        };
    }

    stop {
        if (this.isRunning) {
            this.isRunning = false;
            this.server.stop;
            ("Visual renderer stopped").postln;
        };
    }

    // Create a view with custom rendering capabilities
    createView { |name, bounds, background, customRenderFunc|
        var view, viewData, originalDrawFunc;
        
        view = this.server.createView(name, bounds, background);
        
        if (customRenderFunc.notNil) {
            // Override the default render function
            viewData = this.server.views[name];
            originalDrawFunc = viewData[\view].drawFunc;
            
            viewData[\view].drawFunc_({|v|
                // Call original rendering first
                originalDrawFunc.value(v);
                
                // Then call custom rendering
                customRenderFunc.value(v, viewData, this.server);
            });
        };
        
        ^view;
    }

    // Add effects to a view
    addEffect { |viewName, effectFunc|
        var viewData, originalDrawFunc;
        
        viewData = this.server.views[viewName];
        if (viewData.isNil) {
            ("View" + viewName + "not found").warn;
            ^this;
        };

        originalDrawFunc = viewData[\view].drawFunc;
        
        viewData[\view].drawFunc_({|v|
            originalDrawFunc.value(v);
            effectFunc.value(v, viewData, this.server);
        });
        
        ^this;
    }

    // Grid-based particle system
    addParticleSystem { |viewName, numParticles = 50, bounds|
        var viewData, particles, originalDrawFunc;
        
        viewData = this.server.views[viewName];
        if (viewData.isNil) {
            ("View" + viewName + "not found").warn;
            ^this;
        };

        bounds = bounds ?? Rect(0, 0, 800, 600);
        
        // Create particles
        particles = Array.fill(numParticles, {
            (
                x: bounds.width.rand,
                y: bounds.height.rand,
                vx: 2.0.rand2,
                vy: 2.0.rand2,
                size: 3.0 + 7.0.rand,
                color: Color.hsv(1.0.rand, 0.8, 1.0),
                life: 1.0
            );
        });

        viewData[\particles] = particles;

        // Add particle update and rendering
        originalDrawFunc = viewData[\view].drawFunc;
        
        viewData[\view].drawFunc_({|v|
            originalDrawFunc.value(v);
            
            // Update and render particles
            particles.do { |particle|
                // Update position
                particle[\x] = particle[\x] + particle[\vx];
                particle[\y] = particle[\y] + particle[\vy];
                
                // Bounce off walls
                if (particle[\x] < 0 || particle[\x] > bounds.width) {
                    particle[\vx] = particle[\vx].neg;
                    particle[\x] = particle[\x].clip(0, bounds.width);
                };
                if (particle[\y] < 0 || particle[\y] > bounds.height) {
                    particle[\vy] = particle[\vy].neg;
                    particle[\y] = particle[\y].clip(0, bounds.height);
                };
                
                // Render particle
                Pen.fillColor = particle[\color].alpha_(particle[\life]);
                Pen.addOval(Rect(
                    particle[\x] - particle[\size]/2,
                    particle[\y] - particle[\size]/2,
                    particle[\size],
                    particle[\size]
                ));
                Pen.fill;
            };
        });
        
        ^this;
    }

    // Trail effect
    addTrailEffect { |viewName, alpha = 0.1|
        var viewData, originalDrawFunc;
        
        viewData = this.server.views[viewName];
        if (viewData.isNil) {
            ("View" + viewName + "not found").warn;
            ^this;
        };

        originalDrawFunc = viewData[\view].drawFunc;
        
        viewData[\view].drawFunc_({|v|
            // Semi-transparent overlay for trail effect
            Pen.fillColor = v.background.alpha_(alpha);
            Pen.addRect(v.bounds);
            Pen.fill;
            
            originalDrawFunc.value(v);
        });
        
        // Disable automatic clearing
        viewData[\view].clearOnRefresh_(false);
        
        ^this;
    }

    // Post-processing effects
    addBlurEffect { |viewName, amount = 1|
        // Note: Real blur would require image processing
        // This creates a simple motion blur effect
        ^this.addTrailEffect(viewName, 0.2);
    }

    // Grid overlay
    addGrid { |viewName, spacing = 50, color|
        color = color ?? Color.gray(0.3);
        
        ^this.addEffect(viewName, {|view|
            var bounds, cols, rows, x, y;
            
            bounds = view.bounds;
            cols = (bounds.width / spacing).ceil;
            rows = (bounds.height / spacing).ceil;
            
            Pen.strokeColor = color;
            Pen.width = 1;
            
            // Vertical lines
            cols.do { |i|
                x = i * spacing;
                Pen.moveTo(x @ 0);
                Pen.lineTo(x @ bounds.height);
                Pen.stroke;
            };
            
            // Horizontal lines
            rows.do { |i|
                y = i * spacing;
                Pen.moveTo(0 @ y);
                Pen.lineTo(bounds.width @ y);
                Pen.stroke;
            };
        });
    }

    // Performance monitoring
    addPerformanceMonitor { |viewName|
        var lastTime, frameCount, fps;
        
        lastTime = thisThread.seconds;
        frameCount = 0;
        fps = 60;
        
        ^this.addEffect(viewName, {|view|
            var now;
            
            now = thisThread.seconds;
            frameCount = frameCount + 1;
            
            if ((now - lastTime) > 1.0) {
                fps = frameCount;
                frameCount = 0;
                lastTime = now;
            };
            
            ("FPS:" + fps).drawAtPoint(10@10, Font.default, Color.yellow);
            ("Nodes:" + this.server.nodes.size).drawAtPoint(10@25, Font.default, Color.yellow);
        });
    }

    // Background patterns
    addCheckerboard { |viewName, size = 20, color1, color2|
        color1 = color1 ?? Color.gray(0.1);
        color2 = color2 ?? Color.gray(0.15);
        
        ^this.addEffect(viewName, {|view|
            var bounds, cols, rows, color, rect;
            
            bounds = view.bounds;
            cols = (bounds.width / size).ceil;
            rows = (bounds.height / size).ceil;
            
            rows.do { |row|
                cols.do { |col|
                    color = if ((row + col) % 2 == 0) { color1 } { color2 };
                    rect = Rect(col * size, row * size, size, size);
                    
                    Pen.fillColor = color;
                    Pen.addRect(rect);
                    Pen.fill;
                };
            };
        });
    }

    // Cleanup
    free {
        this.stop;
    }
}