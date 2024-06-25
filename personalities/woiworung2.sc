var m = ~model;
var isPlaying = false;
var synth;
var notes = 60 + [9,11,2-12,9,6-12,11,9,2-12,11,13-12] - 12;
m.midiChannel = 1;
//------------------------------------------------------------
// intial state
//------------------------------------------------------------

SynthDef("woiworung2", {|out,freq = 1000, amp = 0.5, att = 2.02, dec = 0.3, sus = 1, rel = 1, gate = 1, fb = 1.2, ch=10|
		var snd, env;
		env = EnvGen.kr(Env.adsr(att, dec, sus, rel), gate: gate, doneAction: 2);

		snd = SinOsc.ar(freq,
			LocalIn.ar(2) * LFNoise1.ar(0.1,2),
			LFNoise1.ar(ch.lag(0.3),6)
			// LFNoise1.ar(MouseY.kr(0.2,19),MouseX.kr(0.1,4.1))
		).tanh * amp.lag(0.3);
		2.do{
			snd = AllpassL.ar(snd,0.3,{0.1.rand+0.03}!2,5)
		};


	Out.ar(out, snd.tanh );
}).add;


~init = ~init <> {

	synth = Synth(\woiworung2, [\freq, (60+4).midicps, \gate, 1]);

};
~stop = {
	"stop".postln;
	synth.set(\gate,0);
};
//------------------------------------------------------------
// triggers
//------------------------------------------------------------

// example feeding the community
~onEvent = {|e|
	m.com.root = e.root;
	m.com.dur = e.dur;

};



~onHit = {|state|


	// if(state == true,{
	// 	synth = Synth(\sheet1, [\frq, 140.rrand(80), \gate,1]);
	// 	{		 synth.set(\gate,0)}.defer(0.1);
	//
	// 	},{
	//
	// });
};


//------------------------------------------------------------
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------
~next = {|d|

	var a = m.accelMassFiltered * 0.25;
	var ch = (m.accelMassFiltered * 0.25).linlin(0.0,1.0,0.2,19);
	// var pchs = [0,12,24,36,48];
	// var i = (d.sensors.gyroEvent.y.abs / pi) * (pchs.size);
	if(a<0.1,{a=0});
	if(a>0.9,{a=1.0});
	synth.set(\amp, a);
	synth.set(\ch, ch);

	a = m.accelMassFiltered * 0.25;
	if(a < 0.08, {
		if(isPlaying.not,{
			isPlaying = true;
			notes = notes.rotate(-1);
			synth.set(\freq,notes[0].midicps);
		})
	},{
		if(isPlaying,{
			isPlaying = false;
		});
	});


};

~nextMidiOut = {|d|
	// m.midiOut.control(m.midiChannel, 0, m.accelMassFiltered * 64 );
};

//------------------------------------------------------------
// plot with min and max
//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;

~plot = { |d,p|
	// [d.sensors.quatEvent.x, d.sensors.quatEvent.y, d.sensors.quatEvent.z];
	// [m.accelMassFiltered * 0.1, d.sensors.gyroEvent.x * 0.1];
		[m.accelMassFiltered * 0.25];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};
// { Klank.ar(`[[300,600,900,1200], nil, [1, 1, 1, 1]], Impulse.ar(MouseX.kr(3,300), 0, 0.01)) }.play;

// (
// {
// 	var my = MouseY.kr(0.1, 20, 1);
// 	var mx = MouseX.kr(0.00001, 0.1, 1);
// 	var tempo = 8;
// 	var seq = Dseq([30,42,37].stutter(4), inf);
// 	var trig = Impulse.ar(tempo);
// 	var inforce = Trig.ar(trig, tempo.reciprocal);
// 	var outforce = Spring.ar(inforce, my, mx);
// 	var root = Demand.ar(trig, 0, seq);
// 	var freq = (outforce * 54.midicps) + root.midicps;
// 	var env = EnvGen.ar(Env.adsr(0.01,0.3,0.8,1.0),inforce);
// 	SinOsc.ar([freq, freq + (freq*0.03)], 0, 0.5 * env)
// }.play;
// )


