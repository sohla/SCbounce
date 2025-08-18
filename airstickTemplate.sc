
// ------------------------------------------------------------------
// check if we are getting OSC data
// ------------------------------------------------------------------
OSCFunc.trace(true)
OSCFunc.trace(false)


// ------------------------------------------------------------------
// a synth for examples
// ------------------------------------------------------------------
(
SynthDef(\template, {
    |out=0, freq=111, amp=0.5, atk=0.001, dcy=0.1, sus=0.7, rel=1.2, gate=1, detune=1.02, lfof=1.0|
	var lfo = LFCub.ar(lfof);
	var env = EnvGen.ar(Env.adsr(atk, dcy, sus, rel), gate, doneAction:2);
	var sig = SinOsc.ar(freq * [1.0, detune], 0, lfo.linlin(-1,1,0,1)).tanh;
	Out.ar(out, sig * env * amp);
}).add;

)

// ------------------------------------------------------------------
// Ex. 1 : simple mappings to a synth using raw data
// ------------------------------------------------------------------
(
~synth = Synth(\template);
~norm = {|v| (v/pi)};
~oscListener = OSCFunc({ |msg, time, addr, recvPort|
	var quat, euler, pitch, roll, yaw;

	quat = Quaternion.new(
		msg[7].asFloat, // w
		msg[4].asFloat, // x
		msg[5].asFloat, // y
		msg[6].asFloat); // z
	euler = quat.asEuler;

	yaw = (euler[0].asFloat / 2pi) + 0.5;
	roll = (euler[1].asFloat / pi) + 0.5;
	pitch = (euler[2].asFloat / 2pi) + 0.5;

	~synth.set(\freq, pitch.linexp(0,1,50,400));
	~synth.set(\detune, roll.linexp(0,1,1.02,1.2));
	~synth.set(\lfof, yaw.linexp(0,1,1,100));

}, "/1/CombinedDataPacket");

)


// ------------------------------------------------------------------
// free everything
// ------------------------------------------------------------------
(
~oscListener.free;
~synth.free;
)


// ------------------------------------------------------------------
// Ex. 2 : simple mappings to a synth using AirWare data
// ------------------------------------------------------------------
(
~synth = Synth(\template);
~oscListener = OSCFunc({ |msg, time, addr, recvPort|
	var pitch, roll, yaw;


	roll = msg[9].asFloat;
	pitch = msg[10].asFloat;
	yaw = msg[11].asFloat;

	~synth.set(\freq, pitch.linexp(-90.0, 90.0, 40.0, 400));
	~synth.set(\detune, roll.linexp(-180, 180.0, 1.02, 1.2));
	~synth.set(\lfof, yaw.linexp(0.0, 360.0, 1.0, 100.0));

}, "/1/CombinedDataPacket");
)

// ------------------------------------------------------------------
// free everything
// ------------------------------------------------------------------
(
~oscListener.free;
~synth.free;
)


// ------------------------------------------------------------------
// another synth for with auto release
// ------------------------------------------------------------------
(
SynthDef(\template2, {
   |out=0, freq=111, amp=0.7, atk=0.001, dcy=0.4, sus=0.2, rel=1.2, gate=1, detune=1.02, lfof=1.0|
	var lfo = LFCub.ar(lfof);
	var env = EnvGen.ar(Env.perc(atk, dcy), gate, doneAction:2);
	var sig = SinOsc.ar(freq * [1.0, detune], 0, lfo.linlin(-1,1,0,1) * amp * env);
	// DetectSilence.ar(sig, doneAction:2);
	Out.ar(out, sig);
}).add;

)

// ------------------------------------------------------------------
// Ex. 3 : using AirWare data and detecting energy threshold
// ------------------------------------------------------------------
(

~synth = Synth(\template2, [\gate, 0]);
~state = (
		threshold: 5.0,
		noteOn: false,
		gate: false,
		lastTime: 0
	);

~oscListener = OSCFunc({ |msg, time, addr, recvPort|
	var pitch, roll, yaw, energy;

	roll = msg[9].asFloat;
	pitch = msg[10].asFloat;
	yaw = msg[11].asFloat;
	energy= msg[22].asFloat;

	~state.noteOn = (energy > ~state.threshold) && (~state.gate.not);
	~state.gate = (energy > ~state.threshold);

	if(~state.noteOn,{

		if( TempoClock.beats > (~state.lastTime + 0.1),{
			~state.lastTime = TempoClock.beats;
			~synth = Synth(\template2, [\freq, yaw.linlin(-90.0, 90.0, 80.0, 160.0)]);
		});

	});
}, "/1/CombinedDataPacket");
)

// ------------------------------------------------------------------
// free everything
// ------------------------------------------------------------------
(
~oscListener.free;
~synth.free;
)

