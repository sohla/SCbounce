var m = ~model;
var d = ~device;
var isOn = false;
var esp;
var notes = [0,4,7,4,7,12,16,7,12];
var note = notes[0];
var root = 60 - 24;
var filter = {|input,history,friction = 0.5|
					(friction * input + ((1 - friction) * history))
};
var fx = 0,fy = 0,fa = 0, fvx = 0, fvy = 0;
var beat = 0, velocity = 0;

m.midiChannel = 0;

//------------------------------------------------------------	
// intial state
//------------------------------------------------------------	

~init = ~init <> { 

	Pdef(m.ptn,
		Pbind(
			\note, Pseq([0,4,7,4,7,12,16,7,12].stutter(1),inf),
			\args, #[],
		);
	);

	Pdef(m.ptn).set(\dur,0.5);
	Pdef(m.ptn).set(\octave,4);
	Pdef(m.ptn).set(\amp,0.0);
	Pdef(m.ptn).play();

};

//------------------------------------------------------------	
// triggers
//------------------------------------------------------------	
~onEvent = {|e|
};

~onHit = {|state|


};

~onMoving = {|state|
};


//------------------------------------------------------------	
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------	
~next = {|d| 

	// filter data
	fx = filter.(d.blob.center.x, fx, 0.3);
	fy = filter.(d.blob.center.y, fy, 0.3);
	fa = filter.(d.blob.area, fa, 0.3);
	fvx = filter.(d.blob.velocity.x.abs / 80.0, fvx, 0.3);
	fvy = filter.(d.blob.velocity.y.abs / 80.0, fvy, 0.3); // made up scale


	// beat = ( 2.pow(((((rx).abs + (ry).abs) * 200).floor + 1))  ).reciprocal;
	velocity = (fvx + fvy).lincurve(0,0.3,0,1,-2);
	beat = 2.pow( (velocity * 4).ceil).reciprocal;
	if( beat < 0.05, {beat = 0.05});

	Pdef(m.ptn).set(\dur, beat);

			Pdef(m.ptn).set(\amp, fvx + fvy);

	if( d.blob.data.size > 2, {

		if( isOn == false, {

			fvx = 0;
			fvy = 0;

			isOn = true;
			// [d.blob.index, "on"].postln;
			// m.midiOut.noteOn(m.midiChannel, note + root, 40);

			Pdef(m.ptn).set(\root, note);
			Pdef(m.ptn).set(\amp, 0.8);
		});

	},{


		if( isOn == true, {

			fvx = 0;
			fvy = 0;
			isOn = false;
			// [d.blob.index, "off"].postln;
			// m.midiOut.noteOff(m.midiChannel, note + root, 0);
			notes = notes.rotate(-1);
			note = notes[0];
			Pdef(m.ptn).set(\amp,0.0);
		});
	});
};

~nextMidiOut = {|d|

	if( d.blob.data.size > 2, {

		m.midiOut.control(m.midiChannel, 0,  d.blob.center.x * 127 );
		m.midiOut.control(m.midiChannel, 1,  d.blob.center.y * 127 );
		// m.midiOut.control(m.midiChannel, 2,  d.blob.area * 127 );
	});
};			

//------------------------------------------------------------	
// plot with min and max
//------------------------------------------------------------	

~plotMin = 0;
~plotMax = 1;

~plot = { |d,p|
	[fx,fy,fa, velocity]
	//[rx,ry]
};
//------------------------------------------------------------	
// midi control
//------------------------------------------------------------	
~midiControllerValue = {|num,val|

};




