var m = ~model;
var sa, sb, sc;
m.midiChannel = 1;


SynthDef(\pullstretchMono, {|out, amp = 0.8, buffer = 0, envbuf = -1, pch = 1.0, div=1, speed = 0.01, splay = 0.5|
	var pos;
	// var mx,my;
	var sp;
	var mas;
	var len = BufDur.kr(buffer) / div;
	var lfo = LFSaw.kr( (1.0/len) * speed ,1,0.5,0.5);
	// my = MouseY.kr(0.01,1,1.0);//splay


	sp = Splay.arFill(12,
			{ |i| Warp1.ar(1, buffer, lfo, pch,splay, envbuf, 8, 0.1, 2)  },
			1,
			1,
			0
	) * amp.lag(1);

	mas = HPF.ar(sp,245);

	Out.ar(out,mas);
}).add;

//------------------------------------------------------------
// intial state
//------------------------------------------------------------
~init = ~init <> {

	~sampleFolder = PathName("/Users/soh_la/Downloads/Voice recordings Music in Motion 25June2024/converted");
	Buffer.read(s, ~sampleFolder.entries[6].fullPath, action:{|buf|
		"loaded".postln;
		sa = Synth(\pullstretchMono,[\buffer,buf,\pch,0.midiratio, \amp,0.4, \div, 4]);
		sb = Synth(\pullstretchMono,[\buffer,buf,\pch,12.midiratio, \amp,0.3, \div, 4]);
		// sc = Synth(\pullstretchMono,[\buffer,buf,\pch,7.midiratio, \amp,0.2, \div, 4]);
	});
};


~deinit = {
	sa.free;
	sb.free;
	// sc.free;
	s.freeAllBuffers;

};
~stop = {
	"stop".postln;

};

//------------------------------------------------------------
// triggers
//------------------------------------------------------------
~onEvent = {|e|
};

~onHit = {|state|
};

//------------------------------------------------------------
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------
~next = {|d|


	var amp = m.accelMass.linlin(0,1,0,0.5);
	if(amp < 0.01, {amp = 0});
	sa.set(\speed, m.rrateMass.linlin(3,10,0.01,2));
	sb.set(\speed, m.rrateMass.linlin(3,10,0.01,2));
	sc.set(\speed, m.rrateMass.linlin(3,10,0.01,2));

	sa.set(\splay, m.rrateMass.linlin(3,10,0.01,1));
	sb.set(\splay, m.rrateMass.linlin(3,10,0.01,1));
	sc.set(\splay, m.rrateMass.linlin(3,10,0.01,1));

	sa.set(\amp, amp);
	sb.set(\amp, amp);
	sc.set(\amp, amp);
};

~nextMidiOut = {|d|
};

//------------------------------------------------------------
// plot with min and max
//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;

~plot = { |d,p|
	[m.rrateMass * 0.1, m.rrateMassFiltered * 0.1];
	// [m.accelMass * 0.3, m.accelMassFiltered * 0.5];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};

// (
// var a = 1.0.linrand;
// var b = Array.linrand(1,0.0,1.0-a);
// var c = 1.0 - b - a;
// [a,b,c].flat
// )
//
