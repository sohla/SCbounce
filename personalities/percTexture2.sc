var m = ~model;
var buffer;
var sa, sb;

//------------------------------------------------------------
SynthDef(\pullstretchMono, {|out, amp = 0.8, buffer = 0, envbuf = -1, pch = 1.0, div=1, speed = 0.01, splay = 0.5|
	var pos;
	var sp;
	var mas;
	var len = BufDur.kr(buffer) / div;
	var lfo = LFSaw.kr( (1.0/len) * speed * 0.5,1,0.5,0.5);

	sp = Splay.arFill(12,
			{ |i| Warp1.ar(1, buffer, lfo, pch,splay, envbuf, 8, 0.1, 2)  },
			1,
			1,
			0
	) * amp.lag(0.4);

	mas = BLowShelf.ar(sp,145, db:-20);

	Out.ar(out,mas);
}).add;

//------------------------------------------------------------
~init = ~init <> {

	var path = PathName("~/Downloads/yourDNASamples/found.wav");
	postf("loading sample : % \n", path.fileName);

	buffer = Buffer.read(s, path.fullPath, action:{ |buf|
		postf("buffer alloc [%] \n", buf);
		sa = Synth(\pullstretchMono,[\buffer,buf,\pch,0.midiratio, \amp,1, \div, 6]);
		sb = Synth(\pullstretchMono,[\buffer,buf,\pch,-1.midiratio, \amp,1, \div, 8]);
	});
};

~deinit = ~deinit <> {
	sa.free;
	sb.free;
	postf("buffer dealloc [%] \n", buffer);
	buffer.free;

};

//------------------------------------------------------------
~next = {|d|

	var amp = m.accelMass.linlin(0,2,0,3);
	var pch = d.sensors.rotateEvent.y.linlin(0,1,0.25,4);
	if(amp < 0.02, {amp = 0});
	sa.set(\speed, m.accelMass.linlin(0,2.5,0.001,1));
	sb.set(\speed, m.accelMass.linlin(0,2.5,0.001,1));
	//
	sa.set(\splay, m.rrateMass.linlin(3,10,0.01,1));
	sb.set(\splay, m.rrateMass.linlin(3,10,0.01,1));

	sa.set(\amp, amp * 2);
	sb.set(\amp, amp * 2);
	sa.set(\pch, pch);
	sb.set(\pch, pch);
};

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
