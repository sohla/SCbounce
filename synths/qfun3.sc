(
SynthDef(\imuExpandedRhythmSynth, {
    arg out=0, ctrlBus, tempo=2;
    var qw, qx, qy, qz, x, y, z, trig, durSeq, freqSeq, ampSeq, panSeq;
    var sound, env, pan, verb;

    // Read quaternion and XYZ data from control bus (now 7 channels)
    #qw, qx, qy, qz, x, y, z = In.kr(ctrlBus, 7);

    // Normalize the new inputs
    x = x.range(0, 1);
    y = y.range(0, 1);
    z = z.range(0, 1);

    // Create a trigger based on tempo, influenced by qw and x
    trig = Impulse.kr(tempo * (qw.range(0.5, 2)) * (x.range(0.8, 1.2)));

    // Define sequences using Demand UGens, influenced by quaternion and XYZ data
    durSeq = Demand.kr(trig, 0, Dseq([0.25, 0.5, 0.25, 1] * (qx.range(0.5, 2)) * (y.range(0.8, 1.2)), inf));
    freqSeq = Demand.kr(trig, 0, Dseq([60, 67, 72, 65].midicps * (qy.range(0.5, 2)) * (z.range(0.8, 1.2)), inf));
    ampSeq = Demand.kr(trig, 0, Dseq([0.8, 0.6, 0.7, 0.5] * (qz.range(0.5, 1.5)) * (x.range(0.7, 1.3)), inf));
    panSeq = Demand.kr(trig, 0, Dwhite(-1, 1, inf));

    // Create envelopes
    env = EnvGen.ar(Env.perc(0.01, durSeq, curve: y.range(-4, 4)), trig);

    // Create different sound sources with more variety
    sound = Select.ar(TIRand.kr(0, 5, trig),
        [
            SinOsc.ar(freqSeq),
            Saw.ar(freqSeq),
            Pulse.ar(freqSeq, y.range(0.1, 0.9)),
            LFTri.ar(freqSeq),
            LFNoise2.ar(freqSeq),
            Formant.ar(freqSeq, freqSeq * 2, freqSeq * 0.5)
        ]
    );

    // Apply envelope and amplitude sequence
    sound = sound * env * ampSeq;

    // Apply panning
    pan = Pan2.ar(sound, panSeq);

    // Apply a global filter controlled by qw and z
    pan = RLPF.ar(pan, qw.range(500, 5000) * z.range(0.5, 2), y.range(0.1, 0.9));

    // Add some distortion based on x
    pan = (pan * (1 + (x * 5))).tanh;

    // Add reverb based on y
    verb = FreeVerb.ar(pan, y.range(0, 0.5), y.range(0.5, 0.9));
    pan = XFade2.ar(pan, verb, y.range(-1, 1));

    // Final output with soft limiting
    Out.ar(out, Limiter.ar(pan, 0.95));
}).add;
	SynthDef(\mouse, { |bus| Out.kr(bus, [MouseX.kr(0,1.0), MouseY.kr(0,1.0)])}).add;

)

// Example usage
(
// Assuming you have set up your IMU data to be written to a control bus
// Replace 'yourControlBusIndex' with the actual bus index
~imuBus = Bus.control(s, 7); // Now 7 channels for qw, qx, qy, qz, x, y, z
Synth(\mouse, [\bus, ~imuBus.index]);
 ~imuBus.setnAt(4,[0.9,0.1,0.1]);

// Start the synth
x = Synth(\imuExpandedRhythmSynth, [\ctrlBus, ~imuBus, \tempo, 4]);

// You would need to continuously update the control bus with IMU data
// This could be done using OSC, SerialPort, or any other method to get the IMU data into SuperCollider
)

// To stop the synth
x.free;