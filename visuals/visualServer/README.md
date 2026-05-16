# Visual Server for SuperCollider

A pattern-based visual system for SuperCollider that enables real-time visual generation synchronized with audio, modeled after SuperCollider's audio server architecture.

## Features

- **Pattern-Based Visual Composition**: Use familiar SuperCollider patterns (Pbind, Pseq, etc.) for visual creation
- **Live Input Integration**: Real-time sensor data (IMU, touch, etc.) can drive visual parameters
- **Audio-Visual Synchronization**: Seamless integration between audio and visual patterns
- **Flexible Rendering**: Multiple visual synth definitions with custom rendering functions
- **Server Architecture**: Visual nodes with unique IDs, similar to audio synth nodes
- **Effects System**: Post-processing effects like trails, grids, and particles

## Quick Start

1. **Install the classes**:

   Classes live in `Classes/`, docs in `HelpSource/`. Copy the **whole
   directory** (remove any previous flat install first, or you get
   duplicate-class compile errors):
   ```bash
   # Remove old install (older versions had flat .sc files here)
   rm -rf ~/Library/Application\ Support/SuperCollider/Extensions/visualServer

   # Copy the whole folder (brings Classes/ + HelpSource/)
   cp -r visuals/visualServer ~/Library/Application\ Support/SuperCollider/Extensions/visualServer
   ```
   SC compiles `Classes/*.sc` recursively and auto-indexes `HelpSource/`
   (Help browser → "Visual Server" guide, "Visual Parameters" reference,
   or `VisualServer.help`).

2. **Recompile class library:**
   ```
   In SuperCollider: Cmd/Ctrl+Shift+L (or Language > Recompile Class Library)
   ```

3. **Quick test installation:**
   ```supercollider
   // Run this to test if classes are installed correctly
   (thisProcess.nowExecutingPath.dirname +/+ "quickTest.scd").load;
   ```

4. **Initialize system:**
   ```supercollider
   // Event types auto-register at class compile — no manual load needed.

   // Initialize visual server
   ~v = VisualServer.default;
   ~v.start;
   ~view = ~v.createView(\demo, Rect(300, 200, 800, 600));
   ```

5. **Create your first visuals:**
   ```supercollider
   // Colorful random circles
   ~pattern = Vbind(
       \x, Pwhite(-0.8, 0.8),
       \y, Pwhite(-0.8, 0.8), 
       \size, Pexprand(20, 80),
       \color, Pfunc({ Color.hsv(1.0.rand, 0.8, 1.0) }),
       \dur, 0.5,
       \view, \demo
   );
   ~player = ~pattern.vplay;
   ```

## Architecture

### VisualServer
- Manages visual views and nodes
- Provides server-like interface (`vnew`, `vset`, `vfree`)
- Handles rendering loop and node lifecycle

### VisualSynthDef
- Defines reusable visual "instruments"
- Built-in shapes: circle, square, line, pulse, spinner, live
- Custom rendering functions with parameter support

### VisualEvent & VisualPatterns
- Event types: `\visual`, `\vpulse`, `\vline`, `\vlive`, `\audioVisual`
- Pattern classes: `Vbind`, `Vseq`, `Vpar`, `Vrand`, etc.
- Integration with SuperCollider's pattern system

### VisualRenderer
- Effects system (trails, grids, particles)
- Performance monitoring
- Custom rendering pipeline

## Examples

### Basic Patterns
```supercollider
// Expanding pulses
Vpulse(
    \x, 0, \y, 0,
    \startSize, 10, \endSize, 100,
    \color, Color.red,
    \dur, 1,
    \view, \demo
).vplay;

// Random positioned circles
Vbind(
    \x, Pwhite(-0.8, 0.8),
    \y, Pwhite(-0.8, 0.8),
    \size, Pexprand(20, 60),
    \dur, 0.3
).vplay;
```

### Audio-Visual Sync
```supercollider
// Each audio note creates a visual
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

### Live Input Mapping
```supercollider
// Circle that follows sensor data
v.vnew(\live, 1001, \demo, [
    \x, { ~sensorData.accel.x * 0.8 },  // Live function
    \y, { ~sensorData.accel.y * 0.8 },
    \size, { ~sensorData.accel.z.abs * 50 + 30 },
    \color, { Color.hsv(~sensorData.gyro.z.abs/pi, 0.8, 1.0) },
    \dur, inf
]);
```

### Pattern + Live Hybrid
```supercollider
// Pattern modulated by live input
Vbind(
    \x, Pwhite(-0.5, 0.5) + Pfunc({ ~sensors.accel.x * 0.3 }),
    \y, Pwhite(-0.5, 0.5) + Pfunc({ ~sensors.accel.y * 0.3 }),
    \size, Pexprand(20, 60) * Pfunc({ ~sensors.movement + 0.5 }),
    \dur, 0.4
).vplay;
```

## Visual Synth Definitions

### Built-in VDefs
- **circle**: Basic filled/stroked circle
- **square**: Rectangle shape
- **line**: Line between two points
- **pulse**: Animated expanding circle
- **spinner**: Rotating line
- **live**: Circle with live parameter functions

### Custom VDefs
```supercollider
VisualSynthDef(\myShape, { |node, view, elapsed, centerX, centerY, params|
    var x = this.getParam(node, \x, 0) * centerX + centerX;
    var y = this.getParam(node, \y, 0) * centerY + centerY;
    var size = this.getParam(node, \size, 50);
    var color = this.getParam(node, \color, Color.white);
    
    // Custom rendering code here
    Pen.fillColor = color;
    Pen.addOval(Rect(x-size/2, y-size/2, size, size));
    Pen.fill;
}, (x: 0, y: 0, size: 50, color: Color.white));
```

## Live Input Integration

### Stream Wrappers
```supercollider
// Live data stream
LiveStream({ ~sensorData.accel.x }, { |x| x * 0.5 }, 0.05)

// In patterns
Vbind(
    \x, LiveStream({ ~sensors.accel.x }),
    \size, LiveStream({ ~sensors.magnitude * 50 + 20 }),
    \dur, 0.1
).vplay;
```

### Function-based Parameters
```supercollider
// For \live visual synth def
v.vnew(\live, 2000, \view, [
    \x, { ~realTimeData.x },      // Evaluated each frame
    \size, { ~realTimeData.size },
    \color, { Color.hsv(~realTimeData.hue, 0.8, 1.0) }
]);
```

## Effects System

```supercollider
r = VisualRenderer.default;

// Add trail effect
r.addTrailEffect(\demo, 0.1);

// Add grid overlay  
r.addGrid(\demo, 50, Color.gray(0.3));

// Add particle system
r.addParticleSystem(\demo, 100);

// Performance monitoring
r.addPerformanceMonitor(\demo);
```

## Coordinate System

- **Visual coordinates**: -1.0 to 1.0 (relative to view center)
- **Size**: Pixels (50 = 50 pixel radius)
- **Colors**: SuperCollider Color objects
- **Duration**: Seconds (inf = persistent)

## File Structure

```
visuals/visualServer/
├── Classes/                 # SC classes (auto-compiled)
│   ├── VisualServer.sc       # Core server class
│   ├── VisualSynthDef.sc     # Visual instrument definitions
│   ├── VisualEvent.sc        # One-off event factory
│   ├── VisualPatterns.sc     # Pattern classes (Vbind, etc.)
│   ├── VisualRenderer.sc     # Effects and rendering
│   └── VisualEventTypes.sc   # Auto-registers event types (*initClass)
├── HelpSource/              # SCDoc help (auto-indexed)
│   ├── Classes/              # one .schelp per class
│   ├── Guides/VisualServer.schelp
│   └── Reference/VisualParameters.schelp
├── VisualEventTypes.scd     # No-op stub (auto-registered; kept for back-compat)
├── loadVisualServer.scd     # Install instructions
├── quickTest.scd            # Install smoke test
├── examples/
│   ├── basicPatterns.scd    # Basic pattern examples
│   ├── audioVisualSync.scd  # Audio+visual sync
│   ├── liveInput.scd        # Live sensor input
│   └── hybridDemo.scd       # Mixed pattern+live system
└── README.md            # This file
```

Documentation is in `HelpSource/` — open via SC's Help browser
(search **"Visual Server"** for the guide, **"Visual Parameters"** for the
full key reference) or `VisualServer.help`.

## Example Workflows

### 1. Pattern-Only Workflow
For composed, algorithmic visuals using SuperCollider patterns.

### 2. Live-Only Workflow  
For direct sensor-to-visual mapping with immediate response.

### 3. Hybrid Workflow
Patterns provide structure while live input adds real-time modulation and interaction.

### 4. Audio-Visual Workflow
Synchronized audio and visual composition using combined event types.

## Performance Notes

- Default frame rate: 60 FPS
- Node cleanup: Automatic for timed events, manual for infinite duration
- Live parameter evaluation: Every frame for `\live` synth def
- Pattern overhead: Minimal, leverages SC's efficient pattern system

## Integration with Main System

To integrate with your main AirKit system:

1. **Replace current renderer**: Substitute `renderer.scd` with VisualServer system
2. **Migrate visual events**: Convert current event system to VisualSynthDefs  
3. **Pattern integration**: Use Vbind for personality-driven visual patterns
4. **Live input**: Map IMU data to live visual parameters
5. **Audio sync**: Use AVbind for combined audio-visual events

This system provides a foundation for both the declarative power of patterns and the real-time responsiveness of live input, giving you the flexibility to use whichever approach fits each specific use case.