var m = ~model;
m.midiChannel = 6;
m.accelMassThreshold = 0.9;
m.rrateMassThreshold = 0.1;
//------------------------------------------------------------	
// pattern
//------------------------------------------------------------	
Pdef(m.ptn,
	Pbind(
		// \note, Pseq([0,5,9,10,2,7],inf),
		\note, Pseq([0],inf),
		//\root, Pseq([0,5,-2,3].stutter(16),inf),
		\args, #[],
	);
);

//------------------------------------------------------------	
// intial state
//------------------------------------------------------------	

~init = ~init <> { 

	Pdef(m.ptn).set(\dur,0.5);
	Pdef(m.ptn).set(\octave,5);
	Pdef(m.ptn).set(\amp,0.8);
	Pdef(m.ptn).set(\type,\midi);
	Pdef(m.ptn).set(\midiout,m.midiOut);
	Pdef(m.ptn).set(\chan,m.midiChannel);
	Pdef(m.ptn).play();

	// change notes
	Pdef(m.ptn,Pbind( 
		\note, Pseq([-5,0,5,7],inf)
	));

};


//------------------------------------------------------------	
// triggers
//------------------------------------------------------------	
~onEvent = {|e|
};

~onHit = {|state|

	// var vel = 80;

	// if(state == true,{
	// 	m.midiOut.noteOn(m.midiChannel, 60 + m.com.root + 24 , vel);
	// },{
	// 	m.midiOut.noteOff(m.midiChannel, 60 + m.com.root + 24, vel);
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

	var oct = ((0.2 + m.rrateMassFiltered.cubed) * 25).mod(2).floor;

	Pdef(m.ptn).set(\root,m.com.root);

	Pdef(m.ptn).set(\dur,m.com.dur);
//	Pdef(m.ptn).set(\dur,(m.rrateMassFiltered * 7).reciprocal);
	Pdef(m.ptn).set(\amp, 0.4);
	Pdef(m.ptn).set(\octave, 5 + oct);

	m.midiOut.control(m.midiChannel, 0, m.rrateMassFiltered * 127 );
};

//------------------------------------------------------------	
// plot with min and max
//------------------------------------------------------------	

~plotMin = 0;
~plotMax = 3;

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
	// midiOut.control(m.midiChannel, num, val * 127 );

};

