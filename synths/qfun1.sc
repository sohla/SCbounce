(
SynthDef(\imuTransformSynth, {
    arg out=0, ctrlBus, freq=440, modFreq=5, fbackAmt=0.3, delayTime=0.2;
    var qw, qx, qy, qz, signal, modulator, carrier, feedback, delay;

    // Read quaternion data from control bus
    #qw, qx, qy, qz = In.kr(ctrlBus, 4);

    // Normalize quaternion components
    #qw, qx, qy, qz = [qw, qx, qy, qz].normalizeSum;

    // Create modulator
    modulator = SinOsc.ar(modFreq * (qw.range(0.5, 2)));

    // Create carrier with frequency modulation
    carrier = SinOsc.ar(freq * (1 + (modulator * qx.range(0, 1))));

    // Feedback loop
    feedback = LocalIn.ar(1);
    feedback = DelayC.ar(feedback, 1, delayTime * qy.range(0.1, 1));
    feedback = feedback * fbackAmt * qz.range(0, 1);

    // Mix carrier and feedback
    signal = carrier + feedback;

    // Apply distortion based on qw
    signal = (signal * (1 + (qw * 5))).tanh;

    // Send signal to feedback loop
    LocalOut.ar(signal);

    // Apply a filter sweep based on qx
    signal = RLPF.ar(signal, freq * qx.range(1, 8), 0.2);

    // Apply amplitude modulation based on qy
    signal = signal * SinOsc.ar(modFreq * 2 * qy.range(0.5, 2));

    // Stereo panning based on qz
    signal = Pan2.ar(signal, qz.range(-1, 1));

    // Apply an envelope
    signal = signal * EnvGen.kr(Env.asr(0.1, 1, 0.1), 1);
    Out.ar(out, signal);
}).add;

	SynthDef(\mouseX, { |bus| Out.kr(bus, MouseX.kr(0,1.0))}).add;
	SynthDef(\mouseY, { |bus| Out.kr(bus, MouseY.kr(0,1.0))}).add;
	SynthDef(\mouse, { |bus| Out.kr(bus, [MouseX.kr(0,1.0), MouseY.kr(0,1.0)])}).add;

)

// Example usage
(
	var mx = Bus.control(s,1);
	var my = Bus.control(s,1);
// Assuming you have set up your IMU data to be written to a control bus
// Replace 'yourControlBusIndex' with the actual bus index
~imuBus = Bus.control(s, 4);

// Synth(\mouseX, [\bus, mx.index]);
// Synth(\mouseY, [\bus, my.index]);
Synth(\mouse, [\bus, ~imuBus.index]);

~imuBus.setnAt(2,[0.8,0.9]);

// Start the synth
x = Synth(\imuTransformSynth, [
    \ctrlBus, ~imuBus,
    \freq, 220,
    \modFreq, 3,
    \fbackAmt, 0.4,
    \delayTime, 0.3
]);

// You would need to continuously update the control bus with IMU data
// This could be done using OSC, SerialPort, or any other method to get the IMU data into SuperCollider
)

// To stop the synth
x.free;