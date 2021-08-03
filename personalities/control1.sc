var m = ~model;
m.midiChannel = 0;

//------------------------------------------------------------	
// intial state
//------------------------------------------------------------	

~init = ~init <> { 

	Pdef(m.ptn,
		Pbind(
			\note, Prand([0,2,4,6,8],inf),
			\root, Pseq([0,1].stutter(64),inf),
			\col, Pwrap(Pseries(0.0,0.01,inf),0.0,1.0), 
			\xx, Pwrap(Pseries(-2.0,0.1,inf),-2.0,2.0),
			\func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[],
		);
	);

	Pdef(m.ptn).set(\dur,0.5);
	Pdef(m.ptn).set(\octave,5);
	Pdef(m.ptn).set(\amp,1);
	Pdef(m.ptn).play();
};


//------------------------------------------------------------	
// triggers
//------------------------------------------------------------	

// example feeding the community
~onEvent = {|e|

(e.note + (e.octave * 12)).postln;
	~oscVisualOut.sendMsg("/shadow", 
		"shape", 0,
		"duration", e.dur,
		"attack", 0.01,
		"release", 0.8,
		"par1", e.col,//(e.note + (e.octave * 12)).linlin(72,108,0,0.99),//e.param1,//colour
		"par2", 0.2,//(e.note + (oct * 12)).linexp(0,127,2,0.2),//scale
		"par3", e.xx.mod(0.3),//(e.note + (e.octave * 12)).linlin(72,108,-2.5,2.5),//sx
		"par4", (e.xx / 0.3).floor * 0.1,//sy
		"par5", e.xx.mod(0.3),//(e.note + (e.octave * 12)).linlin(72,108,-2.5,2.5),//ex
		"par6", (e.xx / 0.3).floor * 0.1,//ey
		"par7", 0,//e.param2.linlin(1,6,0.1,1), // wobble
		"par8", 0,//e.octave.linlin(3,6,28,10),
		"par9", (e.note + (e.octave * 12)).linlin(0,127,6,1),
		"par10", (e.note + (e.octave * 12)).linlin(72,108,0,360),
	);


};


~onHit = {|state|

	var vel = 100;
	var note = 60 + m.com.root - 24	;

	if(state == true,{
		m.midiOut.noteOn(2, note  , vel);
	},{
		m.midiOut.noteOff(2, note, 0);
	});
};


//------------------------------------------------------------	
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------	
~next = {|d| 

	var oct = ((0.2 + m.rrateMassFiltered.cubed) * 25).mod(3).floor;

	Pdef(m.ptn).set(\dur,(m.accelMassFiltered * 2.4 * m.rrateMassThreshold.reciprocal).reciprocal);
	Pdef(m.ptn).set(\amp, 0.7);
	Pdef(m.ptn).set(\octave, 6 + oct);

};

~nextMidiOut = {|d|
	m.midiOut.control(m.midiChannel, 0, m.accelMassFiltered * 64 );
};			

//------------------------------------------------------------	
// plot with min and max
//------------------------------------------------------------	
~plotMin = -3;
~plotMax = 3;

~plot = { |d,p|
	// d.sensors.accelEvent.z.postln;
	// [m.accelMass, m.accelMassFiltered,m.accelMassAmpThreshold];
	// [m.accelMass + m.rrateMassFiltered, m.accelMassFiltered,m.rrateMassThreshold];
	//[m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	//[d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	[d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};
