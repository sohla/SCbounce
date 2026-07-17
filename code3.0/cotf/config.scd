// COTF show configuration — loaded by cotf/main_cotf.scd BEFORE the shared
// controllers. COTF-only; the composer's main.sc never loads this file.
// OSC contract: ../API.md. Spec: COTF repo,
// docs/superpowers/specs/2026-07-14-airkit-integration-design.md.
(
var room = ("COTF_ROOM".getenv ? "3").asInteger;
var repoRoot = thisProcess.nowExecutingPath.dirname.dirname.dirname;

~cotfRoom = room;
~expectedDevice = "Aggregate (MOTU+BlackHole)";
~patchLabel = "AirKit/Room%".format(room);

// --- data-file pre-check: fail with ONE clear line, not a mid-boot throw ----
[
    repoRoot +/+ "out" +/+ "Beethoven4_4_MSO_beats_2_mul4.txt",
    repoRoot +/+ "data" +/+ "audio" +/+ "Beethoven4_4_MSO.wav",
    repoRoot +/+ "data" +/+ "score" +/+ "tick_index_2.json",
    repoRoot +/+ "out" +/+ "Beethoven4_4_MSO_beats_2_mul4_meta.json",
].do { |p|
    if (File.exists(p).not) {
        Error("COTF: missing data file (from the composer; archive: /Users/m0/cotf-assets/airkit-data/): %".format(p)).throw;
    };
};

// --- server options: Aggregate device, 144 ch, show latency (E2b G4) --------
s.options.outDevice = ~expectedDevice;
s.options.numOutputBusChannels = 144;
s.options.numWireBufs = 1024;
s.options.memSize = 65536;
s.options.blockSize = 128;
// [COTF] latency (E2b G4 knob, real home): request a small CoreAudio IO
// buffer. Audio MIDI Setup has no such knob — each client asks for its own.
// OPT-IN via env COTF_HW_BUFFER (e.g. 128/256): on the 2026-07-14 bench,
// unconditionally requesting 128 against the drift-corrected Aggregate
// produced a server that booted and ticked but emitted pure silence on
// every channel — device default it is until a value is bench-proven.
("COTF_HW_BUFFER".getenv !? { |v|
    if (v.asInteger > 0) { s.options.hardwareBufferSize = v.asInteger };
});
s.options.numBuffers = 2048; // sampler personalities load big libraries (matches composer main.sc)
s.latency = 0.05;
// Two instances coexist: distinct scsynth ports (Room 3 = 57110, Room 2 = 57111).
s.addr = NetAddr("127.0.0.1", if(room == 2, 57111, 57110));

// --- per-seat FINAL output buses ---------------------------------------------
// Room 3 seat N -> BlackHole pair (Aggregate ch 129+): index 128 + 2(N-1)
//   (feeds the existing per-headset webrtc-publishers, unchanged).
// Room 2 -> MOTU outs 9/10 (index 8), the cabled Genelecs (shared).
~cotfFinalBus = { |seat|
    if (room == 3, { 128 + (2 * (seat - 1)) }, { 8 });
};

// [COTF 2026-07-17] Physical chair-speaker outs (Room 3 only): seat N -> MOTU
// out 12..16 (0-based bus 11+(N-1)). Mono per chair. Room 2 has no physical
// counterpart. Boot output mode from COTF_OUTPUT_MODE; ABSENT -> "webrtc",
// preserving pre-chair-speaker behaviour (composer default untouched).
~cotfPhysicalBus = { |seat| 11 + (seat - 1) };
~cotfOutputMode = "COTF_OUTPUT_MODE".getenv ? "webrtc";
if (["physical", "webrtc"].includes(~cotfOutputMode).not) { ~cotfOutputMode = "webrtc" };

// QLab owns Beethoven audio at the show (-> "0"). Bench day: "1" (SC DiskIn
// plays Beethoven on the monitors, no QLab audio needed).
~conductorAudioEnabled = ("COTF_CONDUCTOR_AUDIO".getenv ? "1") == "1";
)
