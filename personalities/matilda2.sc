var m = ~model;
m.midiChannel = 4;

//------------------------------------------------------------	
// intial state
//------------------------------------------------------------	

~init = ~init <> { 

	Pdef(m.ptn,
		Pbind(
	        \degree, Pseq([[0,4,7],[4,7,10],[0,4,7],[4,7,9]], inf),
			\root, Pseq([0,5,-2,3,-4,1,-5].stutter(24),inf),
			\func, Pfunc({|e| ~onEvent.(e)}),
			\octave,Pseq([3,4,5,6,5,4].stutter(3),inf),
			\args, #[],
		);
	);

	Pdef(m.ptn).set(\dur,0.5);
	Pdef(m.ptn).set(\amp,0.2);
	Pdef(m.ptn).play();
};

//------------------------------------------------------------	
// triggers
//------------------------------------------------------------	
~onEvent = {|e|
	// e.postln;
	m.com.root = e.root;
};

~onHit = {|state|

	var vel = 60;
	var oct = [-48,-36].choose;
	var note = 60 + m.com.root + oct;
	var ch = 4;
	if(state == true,{
		m.midiOut.noteOn(ch, note, 110);
		{m.midiOut.noteOff(ch, note, 0)}.defer(0.2);

	},{
	});


};


//------------------------------------------------------------	
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------	
~next = {|d| 

	//Pdef(m.ptn).set(\root,m.com.root);
	Pdef(m.ptn).set(\dur,(m.accelMassFiltered * 1.2 * m.rrateMassThreshold.reciprocal).reciprocal);

};

~nextMidiOut = {|d|
	// m.midiOut.control(m.midiChannel, 0, m.rrateMassFiltered * 127 );
};			

//------------------------------------------------------------	
// plot with min and max
//------------------------------------------------------------	

~plotMin = -5;
~plotMax = 5;

~plot = { |d,p|
	// [(m.rrateMassFiltered * 3).ceil.mod(3)];
	//[m.rrateMassFiltered.pow(3)*2, m.accelMassFiltered, m.com.rrateMass];
	// [d.accelEvent.x, d.accelEvent.y, d.accelEvent.z, d.gyroEvent.x, d.gyroEvent.y, d.gyroEvent.z];

	[m.rrateMassFiltered, m.rrateMassThreshold];

};
