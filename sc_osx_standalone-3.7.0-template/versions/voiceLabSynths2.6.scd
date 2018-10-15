

(

	SynthDef(\opMicInput, {|buffer = 0, channel = 0, gain = 3.0|
		var sig = SoundIn.ar(channel) * gain.dbamp;
		Out.ar(0,[sig,sig,sig,sig]);
	}).send(s);



	SynthDef(\recordSession, {|bufnum = 0|
		var p = In.ar(0,2);
		 DiskOut.ar(bufnum, p);
	}).send(s);




	SynthDef(\recordChild, {|bufnum = 0, gain = 3.0|
		var p = In.ar(1,1);
		 DiskOut.ar(bufnum, p);
	}).send(s);





	SynthDef(\listenToChild, {|gain = 3.0, monitor = 6.0|
		var p = SoundIn.ar(1) * gain.dbamp;
		SendPeakRMS.kr(p,30, 0.8, "/peakListener");
		Out.ar(0,[p * monitor.dbamp,p * monitor.dbamp,0,0]);
	}).send(s);







	// straight forward playing of a buffer and sending levels out
	SynthDef(\playBuffer, {|buffer = 0, note = 69, amp = 1.0|
		var pitch = (note.midicps / 440.0) * BufRateScale.kr(buffer);
		var player = PlayBuf.ar(2, buffer, pitch, doneAction:2) * amp;
		Out.ar(0,[player[0],player[1],player[0],player[1]]);
	}).send(s);



	SynthDef(\playBG, {|buffer = 0, note = 69, amp = 1.0, child = 0.3, op = 0.2|
		var pitch = (note.midicps / 440.0) * BufRateScale.kr(buffer);
		var player = PlayBuf.ar(2, buffer, pitch, loop:1, doneAction:2) * amp;
		Out.ar(0,[player[0]*op,player[1]*op,player[0]*child,player[1]*child]);
	}).send(s);



	// sin impulse for testing
	SynthDef(\sinImpulse,{|freq=440,amp=0.8|
			var trg = Decay2.ar(Impulse.ar(2,0,0.3), 0.01, 0.3);
			var sig = SinOsc.ar(freq,0,trg*amp);
			Out.ar(0,[sig,sig])
		}
	).send(s);






	SynthDef(\shiftme, {|buffer = 0, frq = 52, mul = 131, add = 0.4,delay = 0.04, decay = 0.19|

		var player,voco,kernal,fs = 0,dly;

		player = In.ar(0,1);
		kernal = LFTri.kr(frq,0,mul,add);
		fs = FreqShift.ar(player,kernal);
		dly = CombN.ar((fs).dup,0.3,[delay,delay+0.01],decay) * 1.0;
		
		ReplaceOut.ar(0,[dly,dly,dly,dly]);

	}).send(s);





	SynthDef(\ringme, {|buffer = 0, pitch = 51, range = 1,delay = 0.04, decay = 0.19|

		var player,sel,fol,modulator,dly;

		player = In.ar(0,1);
		fol = (PeakFollower.ar(player,0.01) * range).lag(0,0.1);
		modulator = SinOsc.ar( pitch.midicps + fol.midicps,0,player);
		dly = CombN.ar((modulator).dup,0.3,[delay,delay+0.01],decay) * 1.3;
		
		ReplaceOut.ar(0,[dly,dly,dly,dly]);

	}).send(s);





	SynthDef(\pitchme, {|buffer = 0, pitch = 1.0,frq = 2.4, amp = 0.2, add = 0.3,delay = 0.004, decay = 0.019|

		var player,sq,grainSize = 0.4,dly;

		player = In.ar(0,1);
		sq = PitchShift.ar(player, grainSize,pitch,SinOsc.ar(frq,0,amp,add)).dup;
		dly = CombN.ar(sq,0.3,[delay,delay+0.01],decay) * 1.6;

		ReplaceOut.ar(0,[dly,dly,dly,dly]);

	}).send(s);


	// simple disk out
	SynthDef(\diskOut, {|bufnum|
	    DiskOut.ar(bufnum, In.ar(0,1));
	}).send(s);


)
