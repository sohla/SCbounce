VisualServer {
    classvar <default;
    var <views, <nodes, <nextNodeID;
    var <isRunning = false;

    *initClass {
        default = VisualServer.new;
    }

    *new {
        ^super.new.init;
    }

    // Collect provided shape params from an event into a [k,v,...] array.
    // Only the event's OWN keys are collected (includesKey, not at): keys
    // inherited from SC's default parent event - notably sustain (a
    // #{ ~dur*~legato*~stretch } Function) and legato 0.8 - must NOT leak
    // in, or they break our envelope model. Omitted keys fall back to each
    // VisualSynthDef's defaults / VisualSynthDef.envState defaults.
    // Defined here (not as an environment var) so it resolves correctly
    // when called from inside an event-type function.
    *visualArgsFrom { |env|
        var keys = [\x, \y, \size, \color, \dur, \fill, \rotation,
            \startSize, \endSize, \curve,
            \x1, \y1, \x2, \y2, \width,
            // envelope / lifetime (see VisualSynthDef.envState)
            \stretch, \legato, \sustain, \attack, \release];
        var args = [];
        keys.do { |k|
            if (env.includesKey(k) and: { env[k].notNil }) {
                args = args.add(k); args = args.add(env[k]);
            };
        };
        ^args;
    }

    init {
        views = Dictionary.new;
        nodes = Dictionary.new;
        nextNodeID = 1000;
        // Defensive: built-ins are registered in VisualSynthDef.initClass.
        // Only re-register if something cleared them.
        if (VisualSynthDef.all.isNil or: { VisualSynthDef.all.isEmpty }) {
            VisualSynthDef.initBuiltInDefs;
        };
    }

    // Create a new visual view (like audio server boot)
    createView { |name, bounds, background|
        var view = UserView()
            .bounds_(bounds ?? Rect(100, 100, 800, 600))
            .background_(background ?? Color.black)
            .animate_(true)
            .frameRate_(60)
            .drawFunc_({|v| this.renderView(name, v) });

        views[name] = (
            view: view,
            nodes: Dictionary.new,
            window: Window("Visual Server - " ++ name)
                .bounds_(bounds ?? Rect(100, 100, 800, 600))
                .layout_(VLayout(view))
                .front
        );

        ^view;
    }

    // Visual equivalent of s_new (create synth node)
    vnew { |defName, nodeID, viewName, args|
        var def, node;
        
        def = VisualSynthDef.at(defName);
        if (def.isNil) {
            ("VisualSynthDef" + defName + "not found").warn;
            ^nil;
        };

        nodeID = nodeID ?? this.nextID;
        viewName = viewName ?? \default;

        // Ensure view exists
        if (views[viewName].isNil) {
            this.createView(viewName);
        };

        // Create visual node
        node = (
            def: def,
            nodeID: nodeID,
            viewName: viewName,
            params: def.defaultParams.copy,
            startTime: thisThread.seconds,
            isActive: true
        );

        // Set parameters from args
        if (args.notNil) {
            args.pairsDo { |key, value|
                node[\params][key] = value;
            };
        };

        // Store node
        nodes[nodeID] = node;
        views[viewName][\nodes][nodeID] = node;

        ("Visual node" + nodeID + "created in view" + viewName).postln;
        ^nodeID;
    }

    // Visual equivalent of n_set (set node parameters)
    vset { |nodeID ... args|
        var node;

        node = nodes[nodeID];
        if (node.isNil) {
            ("Visual node" + nodeID + "not found").warn;
            ^this;
        };

        // Accept both vset(id, \k, v, ...) and vset(id, [\k, v, ...])
        if (args.size == 1 and: { args[0].isArray }) { args = args[0] };

        args.pairsDo { |key, value|
            node[\params][key] = value;
        };

        ^this;
    }

    // Visual equivalent of n_free (free node)
    vfree { |nodeID|
        var node, viewName;
        node = nodes[nodeID];
        if (node.isNil) {
            ("Visual node" + nodeID + "not found").warn;
            ^this;
        };

        viewName = node[\viewName];
        nodes.removeAt(nodeID);
        views[viewName][\nodes].removeAt(nodeID);

        ("Visual node" + nodeID + "freed").postln;
        ^this;
    }

    // Get next available node ID
    nextID {
        var id;
        
        id = nextNodeID;
        nextNodeID = nextNodeID + 1;
        ^id;
    }

    // Render a specific view
    renderView { |viewName, view|
        var viewData, now, bounds, centerX, centerY;
        
        viewData = views[viewName];
        if (viewData.isNil) { ^this };

        now = thisThread.seconds;
        bounds = view.bounds;
        centerX = bounds.width / 2;
        centerY = bounds.height / 2;

        // Clear background (skipped when an effect manages it, e.g. trails)
        if (viewData[\clearBackground] ? true) {
            Pen.fillColor = view.background;
            Pen.addRect(view.bounds);
            Pen.fill;
        };

        // Render each active node. Iterate a copy: render funcs may vfree
        // expired nodes, which mutates viewData[\nodes] mid-iteration.
        viewData[\nodes].copy.do { |node|
            if (node[\isActive]) {
                this.renderNode(node, view, now, centerX, centerY);
            };
        };
    }

    // Render a single visual node
    renderNode { |node, view, now, centerX, centerY|
        var def, params, elapsed;
        
        def = node[\def];
        params = node[\params];
        elapsed = now - node[\startTime];

        // Call the visual synth definition's render function
        def.renderFunc.value(node, view, elapsed, centerX, centerY, params);
    }

    // Start the visual server
    start {
        if (isRunning.not) {
            isRunning = true;
            ("Visual server started").postln;
        };
    }

    // Stop the visual server
    stop {
        if (isRunning) {
            isRunning = false;
            this.freeAll;
            ("Visual server stopped").postln;
        };
    }

    // Free all nodes
    freeAll {
        nodes.clear;
        views.do { |viewData|
            viewData[\nodes].clear;
        };
        ("All visual nodes freed").postln;
    }

    // Close all views
    closeAllViews {
        views.do { |viewData|
            viewData[\window].close;
        };
        views.clear;
    }

    // Cleanup
    free {
        this.stop;
        this.closeAllViews;
    }
}