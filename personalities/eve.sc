var m = ~model;
m.midiChannel = 9;

//------------------------------------------------------------	
// pattern
//------------------------------------------------------------	
Pdef(m.ptn,
	Pbind(
		\note, Pseq([0,9,7,0,-3,-5],inf),
		\args, #[],
	);
);

//------------------------------------------------------------	
// intial state
//------------------------------------------------------------	

~init = ~init <> { 
	Pdef(m.ptn).set(\dur,0.5);
	Pdef(m.ptn).set(\octave,8);
	Pdef(m.ptn).set(\amp,0.8);
	Pdef(m.ptn).set(\type,\midi);
	Pdef(m.ptn).set(\midiout,m.midiOut);
	Pdef(m.ptn).set(\chan,m.midiChannel);
	Pdef(m.ptn).play();
};


//------------------------------------------------------------	
// triggers
//------------------------------------------------------------	
~onHit = {|state|

	var ch = 9;
	var n = [0,2,4,5,7,9,11,12].choose;
	var vel = 50;

	if(state == true,{
		m.midiOut.noteOn(m.midiChannel, 60 + n , vel);
	},{
		m.midiOut.noteOff(m.midiChannel, 60 + n , vel);
	});
};

~onMoving = {|state|

};

~onAmp = {|v|
	// TODO
};

//------------------------------------------------------------	
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------	
~next = {|d| 

	Pdef(m.ptn).set(\dur,((m.rrateMassFiltered).pow(0.5)* 4).reciprocal);
	Pdef(m.ptn).set(\amp,0.4);

};

//------------------------------------------------------------	
// plot with min and max
//------------------------------------------------------------	

~plotMin = -1;
~plotMax = 1;

~plot = { |d,p|
	[m.accelMassFiltered, m.rrateMassFiltered, ((m.rrateMassFiltered).pow(0.5)* 1)];
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





