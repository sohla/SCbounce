# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a SuperCollider Visual Server system - a pattern-based visual composition framework that mirrors SuperCollider's audio server architecture. It enables real-time visual generation synchronized with audio using familiar SuperCollider patterns (Pbind, Pseq, etc.).

## Core Architecture

### Class System
The project consists of 5 main SuperCollider classes that must be installed in the Extensions folder:

- **VisualServer.sc**: Core server managing visual views and nodes with server-like interface
- **VisualSynthDef.sc**: Visual instrument definitions (circle, square, line, pulse, spinner, live)  
- **VisualEvent.sc**: Event types for pattern integration (\visual, \vpulse, \vline, \vlive, \audioVisual)
- **VisualPatterns.sc**: Pattern classes (Vbind, Vseq, Vpar, Vrand) extending Pbind
- **VisualRenderer.sc**: Effects system (trails, grids, particles) and performance monitoring

### Key Design Patterns
- **Server-like Architecture**: Uses `vnew`, `vset`, `vfree` commands similar to audio server
- **Pattern Integration**: Visual patterns work seamlessly with SuperCollider's existing pattern system
- **Live Parameter Functions**: Functions evaluated each frame for real-time sensor/data integration
- **Coordinate System**: Normalized -1.0 to 1.0 coordinates relative to view center

## Installation & Setup Commands

### Install Classes
Classes live in `Classes/`, docs in `HelpSource/`. Copy the WHOLE directory
(remove any previous flat install first to avoid duplicate-class errors).
```bash
# Remove old install (older versions had flat .sc files here)
rm -rf ~/Library/Application\ Support/SuperCollider/Extensions/visualServer

# Copy the whole directory (brings Classes/ + HelpSource/)
cp -r /path/to/visuals/visualServer ~/Library/Application\ Support/SuperCollider/Extensions/visualServer

# Alternative: symbolic link (auto-tracks edits)
ln -s /path/to/visuals/visualServer ~/Library/Application\ Support/SuperCollider/Extensions/visualServer
```
SC compiles `Classes/*.sc` recursively and auto-indexes `HelpSource/`
(Help browser → "Visual Server" guide, "Visual Parameters" reference).

### Initialize System
```supercollider
// In SuperCollider: Recompile class library first
Cmd/Ctrl+Shift+L

// Load event types (must be done after class compilation)
(thisProcess.nowExecutingPath.dirname +/+ "VisualEventTypes.scd").load;

// Initialize server and create view
v = VisualServer.default;
v.start;
~view = v.createView(\demo, Rect(200, 200, 800, 600));
```

### Test Installation
```supercollider
// Run quick test
(thisProcess.nowExecutingPath.dirname +/+ "quickTest.scd").load;
```

## SuperCollider Code Style

### Variable Declaration
**CRITICAL**: In SuperCollider, ALL variables must be declared at the top of their scope. This is a strict language requirement.

```supercollider
// Correct - all vars declared at top
{ |x, y|
    var result, temp, color;
    result = x + y;
    temp = result * 2;
    color = Color.hsv(temp/100, 0.8, 1.0);
    color;
}

// WRONG - will cause compilation errors
{ |x, y|
    var result = x + y;    // Error: assignment in declaration
    var temp;              // Error: var declaration after code
    temp = result * 2;
    var color;             // Error: var declaration after code
}
```

## Development Notes

- **No long instructions** - User knows SuperCollider well
- **No usage documentation** - This is experimental code for playing/testing
- **No user guides** - Just code and iterate
- **Focus on code experimentation** - User will test and change continuously
- **Minimal documentation** - Only essential technical details needed

## Current Issues

### VisualSynthDef Registration Problem — RESOLVED
- Root cause: `VisualServer.initClass` populated `VisualSynthDef.all`, then
  `VisualSynthDef.initClass` ran afterwards and reset `all = Dictionary.new`,
  wiping every built-in. initClass order is not guaranteed by a runtime call
  buried in `VisualServer:init`.
- Fix: built-ins are now registered inside `VisualSynthDef.initClass` itself
  (after `all` is created). `VisualServer:init` only re-registers defensively
  if `all` is empty.

## Development Workflow

### 1. Pattern-Based Development
Use for composed, algorithmic visuals:
```supercollider
Vbind(
    \x, Pwhite(-0.8, 0.8),
    \y, Pwhite(-0.8, 0.8), 
    \size, Pexprand(20, 80),
    \color, Pfunc({ Color.hsv(1.0.rand, 0.8, 1.0) }),
    \dur, 0.5,
    \view, \demo
).vplay;
```

### 2. Live Input Integration
For real-time sensor/data mapping:
```supercollider
v.vnew(\live, 1001, \demo, [
    \x, { ~sensorData.accel.x * 0.8 },
    \y, { ~sensorData.accel.y * 0.8 },
    \size, { ~sensorData.accel.z.abs * 50 + 30 },
    \color, { Color.hsv(~sensorData.gyro.z.abs/pi, 0.8, 1.0) }
]);
```

### 3. Audio-Visual Sync
For combined audio-visual composition:
```supercollider
AVbind(
    // Audio
    \instrument, \sine,
    \freq, Pseq([440, 550, 660].midicps, inf),
    \amp, 0.3,
    \dur, 0.5,
    
    // Visual  
    \vx, Pkey(\freq).linlin(440, 660, -0.5, 0.5),
    \endSize, Pkey(\amp) * 200,
    \vcolor, Pfunc({|ev| Color.hsv(ev[\freq].cpsmidi/127, 0.8, 1.0) })
).play;
```

## File Structure

```
visualServer/
├── Classes/                 # SC classes (auto-compiled)
│   ├── VisualServer.sc          # Core server class
│   ├── VisualSynthDef.sc        # Visual instrument definitions
│   ├── VisualEvent.sc           # One-off event factory
│   ├── VisualPatterns.sc        # Pattern classes (Vbind, etc.)
│   └── VisualRenderer.sc        # Effects and rendering system
├── HelpSource/              # SCDoc help (auto-indexed)
│   ├── Classes/                 # one .schelp per class (16)
│   ├── Guides/VisualServer.schelp        # system guide
│   └── Reference/VisualParameters.schelp # full key reference
├── VisualEventTypes.scd     # Event type definitions (load after classes)
├── loadVisualServer.scd     # Installation instructions
├── quickTest.scd            # Test installation
├── examples/
│   ├── basicPatterns.scd    # Basic pattern examples
│   ├── audioVisualSync.scd  # Audio+visual synchronization
│   ├── liveInput.scd        # Live sensor input examples
│   └── hybridDemo.scd       # Mixed pattern+live system
└── README.md               # Complete documentation
```
Documentation lives in `HelpSource/` — open via SC's Help browser
(search "Visual Server" / "Visual Parameters") or `VisualServer.help`.

## Common Development Tasks

### Add New Visual Synth Definition
```supercollider
VisualSynthDef(\myShape, { |node, view, elapsed, centerX, centerY, params|
    var x = this.getParam(node, \x, 0) * centerX + centerX;
    var y = this.getParam(node, \y, 0) * centerY + centerY;
    var size = this.getParam(node, \size, 50);
    var color = this.getParam(node, \color, Color.white);
    
    // Custom rendering code
    Pen.fillColor = color;
    Pen.addOval(Rect(x-size/2, y-size/2, size, size));
    Pen.fill;
}, (x: 0, y: 0, size: 50, color: Color.white));
```

### Add Effects
```supercollider
r = VisualRenderer.default;
r.addTrailEffect(\demo, 0.1);           // Trail effect
r.addGrid(\demo, 50, Color.gray(0.3));  // Grid overlay
r.addParticleSystem(\demo, 100);        // Particle system
r.addPerformanceMonitor(\demo);         // Performance monitoring
```

### Node Management
```supercollider
// Create node
nodeID = v.vnew(\circle, nil, \view, [\x, 0, \y, 0, \size, 50]);

// Update parameters
v.vset(nodeID, \x, 0.5, \color, Color.blue);

// Free node
v.vfree(nodeID);
```

## Performance Considerations

- Default frame rate: 60 FPS
- Live parameter functions evaluated every frame (use sparingly)
- Pattern overhead minimal due to SuperCollider's efficient pattern system
- Automatic node cleanup for timed events, manual cleanup for infinite duration
- Use VisualRenderer performance monitoring for optimization

## Integration Notes

This system is designed to replace existing visual rendering in the larger SCbounce/AirKit project. It provides both declarative pattern-based composition and real-time live input responsiveness, suitable for interactive musical performance systems.