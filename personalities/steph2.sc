var m = ~model;
var pb;
m.midiChannel = 2;
m.accelMassThreshold = 0.8;
m.rrateMassThreshold = 0.1;

//------------------------------------------------------------	
// intial state
//------------------------------------------------------------	

~init = ~init <> { 

	Pdef(m.ptn,
		pb = Pbind(
			\dd, Pfunc{(m.rrateMassFiltered * 6).reciprocal},
			\rt, Pfunc{m.com.root},
	        \degree, Pseq([0,4].stutter((4 - (m.rrateMassFiltered * 4).floor).asInteger), inf),
	 		\dur, Pseq([1.0,0.5,0.5,0.5], inf) * Pkey(\dd) * 2,
	 		\octave, Pseq([3,4].stutter((4 - (m.rrateMassFiltered * 4).floor).asInteger), inf),
	 		\root, Pkey(\rt),
			\args, #[],
		);
	);
};

//------------------------------------------------------------	
// triggers
//------------------------------------------------------------	
~onEvent = {|e|
};

~onHit = {|state|

	// var vel = 10;
	// var note = [0,4,7,9,7,4,2].choose;
	// if(state == true,{
	// 	m.midiOut.noteOn(m.midiChannel, 60 + m.com.root + note + 24, vel);
	// 	{m.midiOut.noteOff(m.midiChannel, 60 + m.com.root + note + 24, vel);}.defer(0.3);
	// },{

	// });
};

~onMoving = {|state|

	if(state == true,{
		Pdef(m.ptn).resume();
	},{
		Pdef(m.ptn).pause();
	});
};

~onAmp = {|v|
	// TODO
};

//------------------------------------------------------------	
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------	
~next = {|d| 
};

~nextMidiOut = {|d|
	m.midiOut.control(m.midiChannel, 0, (63 + (m.rrateMassFiltered * 64)).asInteger );
};			

//------------------------------------------------------------	
// plot with min and max
//------------------------------------------------------------	

~plotMin = 0;
~plotMax = 8;

~plot = { |d,p|
	// [(m.rrateMassFiltered * 3).ceil.mod(3)];
	[8 - (m.rrateMassFiltered * 8).floor, m.accelMassFiltered, m.com.rrateMass];
};
//------------------------------------------------------------	
// midi control
//------------------------------------------------------------	
~midiControllerValue = {|num,val|

	//[num,val].postln;

	// if(num == 4,{ threshold = 0.01 + (val * 0.99)});

	// threshold = threshold * 2;
	//m.midiOut.control(m.midiChannel, 65, val * 127 );

};
