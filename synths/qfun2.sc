(
SynthDef(\imuRhythmSynth, {
    arg out=0, ctrlBus, tempo=2;
    var qw, qx, qy, qz, trig, durSeq, freqSeq, ampSeq, panSeq;
    var sound, env, pan;

    // Read quaternion data from control bus
    #qw, qx, qy, qz = In.kr(ctrlBus, 4);

    // Create a trigger based on tempo, influenced by qw
    trig = Impulse.kr(tempo * (qw.range(0.5, 2)));

    // Define sequences using Demand UGens, influenced by quaternion data
    durSeq = Demand.kr(trig, 0, Dseq([0.25, 0.5, 0.25, 1] * (qx.range(0.5, 2)), inf));
    freqSeq = Demand.kr(trig, 0, Dseq([60, 67, 72, 65].midicps * (qy.range(0.5, 2)), inf));
    ampSeq = Demand.kr(trig, 0, Dseq([0.8, 0.6, 0.7, 0.5] * (qz.range(0.5, 1.5)), inf));
    panSeq = Demand.kr(trig, 0, Dwhite(-1, 1, inf));

    // Create envelopes
    env = EnvGen.ar(Env.perc(0.01, durSeq), trig);

    // Create different sound sources
    sound = Select.ar(TIRand.kr(0, 3, trig),
        [
            SinOsc.ar(freqSeq),
            Saw.ar(freqSeq),
            Pulse.ar(freqSeq, 0.3),
            LFTri.ar(freqSeq)
        ]
    );

    // Apply envelope and amplitude sequence
    sound = sound * env * ampSeq;

    // Apply panning
    pan = Pan2.ar(sound, panSeq);

    // Apply a global low-pass filter controlled by qw
    pan = RLPF.ar(pan, qw.range(500, 5000), 0.2);

    // Output
    Out.ar(out, pan);
}).add;

	SynthDef(\mouse, { |bus| Out.kr(bus, [MouseX.kr(0,1.0), MouseY.kr(0,1.0)])}).add;

)

// Example usage
(
// Assuming you have set up your IMU data to be written to a control bus
// Replace 'yourControlBusIndex' with the actual bus index
~imuBus = Bus.control(s, 4);

Synth(\mouse, [\bus, ~imuBus.index]);
~imuBus.setnAt(2,[0.1,0.1]);

// Start the synth
x = Synth(\imuRhythmSynth, [\ctrlBus, ~imuBus, \tempo, 4]);

// You would need to continuously update the control bus with IMU data
// This could be done using OSC, SerialPort, or any other method to get the IMU data into SuperCollider
)

// To stop the synth
x.free;