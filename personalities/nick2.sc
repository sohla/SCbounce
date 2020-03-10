var m = ~model;
var cr = [0,-2,3,-4,-5].stutter(5);
m.midiChannel = 2;
//------------------------------------------------------------	
// intial state
//------------------------------------------------------------	

~init = ~init <> { 

	Pdef(m.ptn,
		Pbind(
			\note, Pseq([[-5,0,4]],inf),
			\args, #[],
		);
	);
	Pdef(m.ptn).set(\dur,0.3);
	Pdef(m.ptn).set(\octave,5);
	Pdef(m.ptn).set(\amp,0.2);
	Pdef(m.ptn).set(\strum,0.2);


	Pdef(m.ptn,Pbind( 
		\note, Pseq([[-5,0,4],[-5,0,5],[-5,0,2]].stutter(8),inf),
		//	\octave, Prand([5,6,7,8,9],inf),
	));

	Pdef(m.ptn).play();


};

//------------------------------------------------------------	
// triggers
//------------------------------------------------------------	
~onEvent = {|e|
};

~onHit = {|state|

	var vel = 55;
	var oct = [-48,-36];
	var o = 0;
	var note = 60 + m.com.root + o;
	if(state == true,{

		cr = cr.rotate(-1);
		m.com.root = cr[0];
		o = oct.choose;
		
		note = 60 + m.com.root + o;
		m.midiOut.noteOn(10, note, vel);
		{m.midiOut.noteOff(10, note, vel);}.defer(1);
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


//------------------------------------------------------------	
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------	
~next = {|d| 

	Pdef(m.ptn).set(\root,m.com.root);
	Pdef(m.ptn).set(\amp,0.3);
	Pdef(m.ptn).set(\dur,(1 + (m.accelMassFiltered * 2.2 * m.rrateMassThreshold.reciprocal)).reciprocal);//TODO
	Pdef(m.ptn).set(\strum, 0.3 - (m.rrateMassFiltered * 0.2));
	Pdef(m.ptn).set(\octave, 5 + (m.rrateMassFiltered * 2).floor);
};

~nextMidiOut = {|d|
	m.midiOut.control(m.midiChannel, 0, m.accelMassFiltered * 40 );
};			

//------------------------------------------------------------	
// plot with min and max
//------------------------------------------------------------	

~plotMin = 0;
~plotMax = 1;

~plot = { |d,p|
	[m.accelMassFiltered];
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





