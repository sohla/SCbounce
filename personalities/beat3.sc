var m = ~model;
var d = ~device;

var isOn = false;
var cr = [0,2,4,5,7,9];
var slow =0;

m.midiChannel = 0;
m.accelMassAmpThreshold = 0.2;
m.rrateMassThreshold = 0.01;

//------------------------------------------------------------	
// intial state
//------------------------------------------------------------	

~init = ~init <> { 

	Pdef(m.ptn,
		Pbind(
			// \note, Pseq([0],inf),
			// \func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[],
		);
	);

	Pdef(m.ptn).set(\dur,0.18);
	Pdef(m.ptn).set(\octave,3);
	Pdef(m.ptn).set(\amp,0.9);

	// change notes
	Pdef(m.ptn,Pbind( 
		\func, Pfunc({|e| ~onEvent.(e)}),
		\note, Pseq([0,1,2,3,4,5,6,7,8,9,10,11,12,13,15,14],inf)
		// \note, Pseq([1,3,5,7,9,11],inf)
	));
	//Pdef(m.ptn).play();
};

//------------------------------------------------------------	
// triggers
//------------------------------------------------------------	
~onEvent = {|e|

	var width = d.blob.rect.width;


	width.trunc(25).ceil.postln;

	if( d.blob.area > 52,{
		m.midiOut.control(m.midiChannel, 0, 1 );
	},{
		m.midiOut.control(m.midiChannel, 0, 0 );
	});
	m.midiOut.control(m.midiChannel, 1, d.blob.rect.width.linlin(100,220,0,127) );
	m.midiOut.control(m.midiChannel, 2, d.blob.rect.top.linexp(0,100,127,80) );
	m.midiOut.control(m.midiChannel, 3, d.blob.rect.top.linlin(0,80,127,0) );
	m.midiOut.control(m.midiChannel, 4, d.blob.rect.width.linexp(100,220,20,127) );


	
};

~onHit = {|state|

	if(state == true,{
		Pdef(m.ptn).resume();
	},{
		Pdef(m.ptn).pause();
	});

};

~onMoving = {|state|

	// if(state == true,{
	// 	Pdef(m.ptn).set(\amp, 0.9);
	// },{
	// 	Pdef(m.ptn).set(\amp, 0.0);
	// });
};


//------------------------------------------------------------	
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------	
~next = {|d| 

	m.accelMassAmp = (d.blob.center.x / 450.0) + (d.blob.center.y / 450.0);


};

~nextMidiOut = {|d|

	// if(m.isHit,{
	// 	d.blob.rect.top.postln;
	// 	m.midiOut.control(m.midiChannel, 1, d.blob.rect.top.linlin(80,25,0,127) );
	// });

};			
//------------------------------------------------------------	
// plot with min and max
//------------------------------------------------------------	
~plotMin = 0;
~plotMax = 10;

~plot = { |d,p|

	[m.accelMassAmp];
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
