(
SynthDef('kaoslooper', {arg in=0, out=0, buf, gate=1, t_rec=0, t_reset=1, t_chop=0, plvl=1, switch=0,
	frombeat=0, grainsize=1, numbeats=4, numsubcycles=4, amp=1.0, wet=0.5;
	var dry, loopgate, time, grainlength, grainframes, subcycles, cycles, grains, trigcounter,
	grainstart, grainend, rlvl, xfade, playenv, prelevelenv, env, player, phase, grainer, fx;

	// controls loopgate and rlvl
	trigcounter = PulseCount.kr(t_rec, t_reset);
	// for the fader and for loopbuf as a trigger
	xfade = Lag.kr(switch.linlin(0, 1, -1, 1), 0.1);

	// audio input.
	dry = SoundIn.ar(in ! 2, 2);

	// loopgate is opened when closing the loop and reset by t_reset
	loopgate = trigcounter > 1;
	playenv = EnvGen.kr(Env.asr(0.05, 1.0, 0.05), loopgate);
	env = EnvGen.kr(Env.asr(0.04, 1, 0.04), gate, doneAction:2);
	prelevelenv = 1 - EnvGen.kr(Env.asr(0.1, 1, 0.1), t_chop);

	// controls writing into buffer
	rlvl = EnvGen.kr(Env.asr(0.05, 1.0, 0.05), trigcounter % 2);

	// outputs the length of the loop. reset by loopgate (which becomes one on first rec trig and is 1 until reset)
	time = Latch.kr(Timer.kr(t_rec), loopgate);
	// length of smallest subdivision (default 16) of loop length
	grainlength = time / (numbeats * numsubcycles);
	grainframes = (grainlength * SampleRate.ir).round(1);

	// make new grain starts and lengths derived from grainsize and frombeat
	grainstart = grainframes * frombeat * numsubcycles;
	grainend = grainstart + (grainframes * grainsize);

	// triggers on every subcycle
	subcycles = TDuty.kr(grainlength, loopgate, 1) * loopgate;
	// triggers at each loop cycle beginning + extra trigger at start of first recording
	cycles = Trig.kr(PulseCount.kr(subcycles, t_reset) % (numbeats * numsubcycles), 0.01) + Trig.kr(trigcounter, 0.01);
	// triggers on every grain (subcycles retriggers?!)
	grains = TDuty.kr(grainlength * grainsize, switch, 1);

	// players and recorder
	player = PlayBuf.ar(2, buf, BufRateScale.kr(buf), cycles, loop:1);
	//grainer is a BufRd triggered by grains
	phase = Phasor.ar(grains, 1, grainstart, grainend, grainstart);
	grainer = BufRd.ar(2, buf, phase);
	RecordBuf.ar(dry <! player, buf, recLevel: rlvl, preLevel:plvl, loop:1, trigger: cycles);
	fx = LinXFade2.ar(player, grainer, xfade, playenv);
	// output fader between player and grainer
	Out.ar(out, XFade2.ar(dry, fx, wet * 2 - 1 ) * env * amp);
}).add;
)

// allocate buffer. Your loops should be shorter than the buffer
b = Buffer.alloc(s, s.sampleRate * 16, 2)

// start synth
l = Synth('kaoslooper', ['in', 0, 'buf', b])

// start recording first loop
l.set(\t_rec, 1)
// stop recording, start looping
l.set(\t_rec, 1)

// switch to stutter thingy
l.set(\switch, 1)

// default is 4 beats with 4 subcycles each

// start from beat 1
l.set(\frombeat, 1)

// go back to beat 0
l.set(\frombeat, 1)

// change number of repeated subcycles
l.set(\grainsize, 0.5)

// switch back to regular loop
l.set(\switch, 0)

// overdub
l.set(\t_trig, 1)

// stop overdubbing
l.set(\t_trig, 1)

// reset if you want to record a new loop
l.set(\t_reset, 1)
b.zero


//The End, clean up
l.release 
b.free


