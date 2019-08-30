var m = ~model;
var cr = [0,5,-2,3,-4,1,-5];
m.midiChannel = 1;
m.accelMassThreshold = 0.5;
m.rrateMassThreshold = 0.1;
//------------------------------------------------------------	
// intial state
//------------------------------------------------------------	

~init = ~init <> { 

	Pdef(m.ptn,
		Pbind(
			\note, Pseq([[-5,0,4]],inf),
			// \strum, 0.22,
			\args, #[],
		);
	);
	Pdef(m.ptn).set(\dur,0.5);
	Pdef(m.ptn).set(\octave,5);
	Pdef(m.ptn).set(\amp,0.2);


	Pdef(m.ptn,Pbind( 
		\note, Pseq([[-5,0,4],[-5,0,5],[-5,0,2]],inf),
		// \strum, 0.12,
	));


};

//------------------------------------------------------------	
// triggers
//------------------------------------------------------------	
~onEvent = {|e|
};

~onHit = {|state|

	var vel = 80;

	if(state == true,{
		m.com.root = cr.[0];
		cr = cr.rotate(-1);

		m.midiOut.noteOn(m.midiChannel, 60 + m.com.root - 24 , vel);
		{m.midiOut.noteOff(m.midiChannel, 60 + m.com.root - 24, vel);}.defer(1);
	},{
	});
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

	Pdef(m.ptn).set(\root,m.com.root);
	Pdef(m.ptn).set(\dur,(m.rrateMassFiltered * 5).reciprocal);

	// var oct = ((0.2 + m.rrateMassFiltered.cubed) * 25).mod(4).floor;

	// Pdef(m.ptn).set(\dur,(m.rrateMassFiltered * 14).reciprocal);
	// Pdef(m.ptn).set(\amp, 0.4);
	// Pdef(m.ptn).set(\octave, 2 + oct);

};

~nextMidiOut = {|d|
	m.midiOut.control(m.midiChannel, 0, m.rrateMassFiltered * 70 );
};			

//------------------------------------------------------------	
// plot with min and max
//------------------------------------------------------------	

~plotMin = 0;
~plotMax = 1;

~plot = { |d,p|
	[m.rrateMassFiltered, m.accelMassFiltered];
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





