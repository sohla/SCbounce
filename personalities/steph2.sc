var m = ~model;
m.midiChannel = 2;
m.accelMassThreshold = 0.8;
m.rrateMassThreshold = 0.1;

//------------------------------------------------------------	
// intial state
//------------------------------------------------------------	

~init = ~init <> { 

	// Pdef(m.ptn,
	// 	Pbind(
	//         \degree, Pseq([0,2,5,4].stutter(6), inf),
	// 		\args, #[],
	// 	);
	// );
	// Pdef(m.ptn).set(\dur,1);

	// Pdef(m.ptn,Pbind( 
	// 	\dur, Pseq([0.25,0.25,1.0], inf),
	// 	\octave, 3
	// ));

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

	Pdef(m.ptn,Pbind( 
		\degree, Pseq([0,2,4,1].stutter(1), inf),
		\root, m.com.root,
		\dur, Pseq([0.5,0.5,1.0] * (m.rrateMassFiltered * 8).reciprocal, inf),
		\octave, 3
	));

	m.midiOut.control(m.midiChannel, 0, (63 + (m.rrateMassFiltered * 64)).asInteger );

};

//------------------------------------------------------------	
// plot with min and max
//------------------------------------------------------------	

~plotMin = 0;
~plotMax = 1;

~plot = { |d,p|
	// [(m.rrateMassFiltered * 3).ceil.mod(3)];
	[m.rrateMassFiltered, m.accelMassFiltered, m.com.rrateMass];
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
