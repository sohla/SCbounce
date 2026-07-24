/*
VOsc3 wavetable synth with UI + on-the-fly wavetable generators.
================================================================
- Bank of N adjacent buffers, each holding one wavetable.
- VOsc3.ar sweeps continuously through the bank via `bufpos`;
  three internal detuned oscillators share the same bufpos.
- MoogFF resonant LPF with independent envelope on cutoff.
- ADSR amp + separate ADSR filter env.
- GUI: bank generator selector, wavetable-position slider (live
  waveform display), synth params, mini 8-note keyboard.
- Serum-style generator functions — several algorithms that fill
  the bank with an *evolving* series of waveforms. New generators
  drop into the ~generators dict.

Evaluate blocks top-to-bottom. The final `~makeUI.()` opens the
window. Cleanup at the bottom.
*/

// ---------------------------------------------------------------- CONSTANTS
(
~wtSize  = 1024;    // samples per wavetable (must be power of 2 for VOsc3)
~wtCount = 32;      // bank size — VOsc3 sweeps across bufpos [0..wtCount-3]
)

// ---------------------------------------------------------------- WAVETABLE GENERATORS
// Each entry is a function of (i, n) → phase-function fn(x) where x∈[0,1]
// and returns amp∈[-1,1]. waveFill evaluates fn across the wavetable.
// Add new generators here — they show up in the UI automatically.
(
~generators = (

	sine: { |i, n|
		// Pure sine at every position — baseline / silent morph.
		{ |x| sin(x * 2pi) }
	},

	saw_morph: { |i, n|
		// Additive: sine at pos 0, up to 16 harmonics at pos n-1.
		// Classic wavetable "brightness sweep".
		var h = (i / (n - 1) * 16 + 1).round.asInteger.max(1);
		{ |x| (1..h).sum({ |k| sin(x * 2pi * k) / k }) }
	},

	square_morph: { |i, n|
		// Odd-harmonic additive — sine → square shape.
		var h = (i / (n - 1) * 15).round.asInteger + 1;
		{ |x| (1, 3 .. h).sum({ |k| sin(x * 2pi * k) / k }) }
	},

	fold: { |i, n|
		// Sine driven harder into fold-back distortion.
		var gain = (i / (n - 1) * 7) + 1;
		{ |x| (sin(x * 2pi) * gain).fold(-1, 1) }
	},

	formant: { |i, n|
		// Two harmonics moving apart — vowel-formant flavour.
		var f1 = 1 + (i / (n - 1) * 4).round;
		var f2 = f1 + 2 + (i / (n - 1) * 6).round;
		{ |x| sin(x * 2pi * f1) + (sin(x * 2pi * f2) * 0.5) }
	},

	additive_random: { |i, n|
		// Fixed random-amplitude harmonic series per bank position.
		// Same seed → same waveform, so the sweep is repeatable.
		var amps = { |k| (i + 1 + k).hash.abs / 2147483647 } ! 8;
		{ |x| (1..8).sum({ |k| amps[k - 1] * sin(x * 2pi * k) / k }) }
	},

	sync: { |i, n|
		// Wrapped-phase sine — sync-like harmonics as ratio grows.
		var ratio = 1 + (i / (n - 1) * 6);
		{ |x| sin((x * ratio).mod(1) * 2pi) }
	},

	pwm: { |i, n|
		// Pulse-width morph via two mirrored sawtooth halves.
		var w = 0.1 + (i / (n - 1) * 0.8);
		{ |x| if (x < w) { (x / w * 2 - 1) } { ((x - w) / (1 - w) * -2 + 1) } }
	},
);

~makeWavetable = { |fn, size|
	Signal.newClear(size).waveFill(fn, 0.0, 1.0).normalize.asWavetable
};
)

// ---------------------------------------------------------------- BUFFER BANK
// allocConsecutive guarantees adjacent bufnums (required by VOsc3).
// ~wtCache holds the raw (pre-asWavetable) Signals for GUI drawing.
(
~wtBufs  = Buffer.allocConsecutive(~wtCount, s, 2 * ~wtSize, 1);
~wtCache = Array.newClear(~wtCount);

~loadBank = { |genKey|
	fork {
		var genFn = ~generators[genKey];
		s.sync;
		~wtBufs.do({ |buf, i|
			var fn  = genFn.(i, ~wtCount);
			var sig = Signal.newClear(~wtSize).waveFill(fn, 0.0, 1.0).normalize;
			~wtCache[i] = sig;
			buf.sendCollection(sig.asWavetable, 0, 0.01);
		});
		("bank loaded: " ++ genKey).postln;
	};
};

~loadBank.(\saw_morph);
)

// ---------------------------------------------------------------- SYNTHDEF
// Persistent synth — no doneAction. gate re-triggers ADSR on 0→1.
// bufpos = absolute bufnum of the first buffer + position offset.
(
SynthDef(\vosc3wt, {
	|out=0, bufpos=0, freq=220, gate=0, amp=0.3, pan=0,
	 detune=0.03,
	 cutoff=1200, res=0.4, fenvAmt=3000,
	 atk=0.01, dec=0.2, sus=0.6, rel=0.5,
	 fatk=0.02, fdec=0.35, fsus=0.4, frel=0.5|

	var ampEnv  = EnvGen.kr(Env.adsr(atk, dec, sus, rel), gate);
	var filtEnv = EnvGen.kr(Env.adsr(fatk, fdec, fsus, frel), gate);

	// Three detuned freqs feed VOsc3's three internal oscillators.
	// Small unison spread — chorus without cluttering the wavetable.
	var f1 = freq * (0 - detune).midiratio;
	var f2 = freq;
	var f3 = freq * detune.midiratio;

	var sig  = VOsc3.ar(bufpos, f1, f2, f3, 0.33);
	var fCut = (cutoff + (filtEnv * fenvAmt)).clip(20, 18000);
	var filt = MoogFF.ar(sig, fCut, res * 4);

	Out.ar(out, Pan2.ar(filt * ampEnv * amp, pan));
}).add;
)

// ---------------------------------------------------------------- LIVE SYNTH INSTANCE
// Keep one persistent synth; the UI .sets its params and toggles gate.
(
if(~synth.notNil, { ~synth.free });
~synth = Synth(\vosc3wt, [\gate, 0, \bufpos, ~wtBufs[0].bufnum]);
)

// ---------------------------------------------------------------- STATE + PATTERN
// ~params mirrors every UI slider so pattern events can read current
// knob positions live via Pfunc. ~setParam is the slider action hook
// that keeps both the persistent synth AND the mirror in sync.
// ~vosc3wtE is a per-event variant (doneAction: 2) for pattern use.
(
~params = (
	bufpos: 0,          // *offset* into bank; pattern adds ~wtBufs[0].bufnum
	detune: 0.03,
	cutoff: 1200, res: 0.4, fenvAmt: 3000,
	atk: 0.01,  dec: 0.2,  sus: 0.6, rel: 0.5,
	fatk: 0.02, fdec: 0.35, fsus: 0.4, frel: 0.5,
	amp: 0.3
);

~patParams = (
	root:    48,          // MIDI note the scale sits on (C3 = 48)
	scale:   \pentatonic,
	dur:     0.25,        // seconds between events
	octaves: 3            // range: pick from octaves 0..N-1 above root
);

~scales = (
	major:      [0, 2, 4, 5, 7, 9, 11],
	minor:      [0, 2, 3, 5, 7, 8, 10],
	pentatonic: [0, 2, 4, 7, 9],
	minorPent:  [0, 3, 5, 7, 10],
	whole:      [0, 2, 4, 6, 8, 10],
	chromatic:  (0..11),
);

~setParam = { |k, v|
	~params[k] = v;
	~synth.set(k, v);
};

// Per-event SynthDef — identical to \vosc3wt but self-frees on env end.
SynthDef(\vosc3wtE, {
	|out=0, bufpos=0, freq=220, gate=1, amp=0.3, pan=0,
	 detune=0.03,
	 cutoff=1200, res=0.4, fenvAmt=3000,
	 atk=0.01, dec=0.2, sus=0.6, rel=0.5,
	 fatk=0.02, fdec=0.35, fsus=0.4, frel=0.5|

	var ampEnv  = EnvGen.kr(Env.adsr(atk, dec, sus, rel), gate, doneAction: 2);
	var filtEnv = EnvGen.kr(Env.adsr(fatk, fdec, fsus, frel), gate);
	var f1 = freq * (0 - detune).midiratio;
	var f2 = freq;
	var f3 = freq * detune.midiratio;
	var sig  = VOsc3.ar(bufpos, f1, f2, f3, 0.33);
	var fCut = (cutoff + (filtEnv * fenvAmt)).clip(20, 18000);
	var filt = MoogFF.ar(sig, fCut, res * 4);
	Out.ar(out, Pan2.ar(filt * ampEnv * amp, pan));
}).add;

// Pattern reads live from ~params (synth voice) and ~patParams (rhythm
// / pitch selection) — every UI slider affects the next event.
Pdef(\vosc3wt_pat,
	Pbind(
		\instrument, \vosc3wtE,
		\midinote, Pfunc {
			var scale = ~scales[~patParams[\scale]] ? [0];
			~patParams[\root] + scale.choose + (~patParams[\octaves].rand * 12);
		},
		\dur,     Pfunc { ~patParams[\dur] },
		\amp,     Pfunc { ~params[\amp] },
		\bufpos,  Pfunc { ~wtBufs[0].bufnum + ~params[\bufpos] },
		\detune,  Pfunc { ~params[\detune] },
		\cutoff,  Pfunc { ~params[\cutoff] },
		\res,     Pfunc { ~params[\res] },
		\fenvAmt, Pfunc { ~params[\fenvAmt] },
		\atk,     Pfunc { ~params[\atk] },
		\dec,     Pfunc { ~params[\dec] },
		\sus,     Pfunc { ~params[\sus] },
		\rel,     Pfunc { ~params[\rel] },
		\fatk,    Pfunc { ~params[\fatk] },
		\fdec,    Pfunc { ~params[\fdec] },
		\fsus,    Pfunc { ~params[\fsus] },
		\frel,    Pfunc { ~params[\frel] },
		\pan,     Pwhite(-0.3, 0.3),
	);
);
)

// ---------------------------------------------------------------- GUI
(
~makeUI = {
	var w, wtView, updateWT;
	var currentPos = 0;

	w = Window("VOsc3 Wavetable Synth", Rect(180, 80, 640, 1000))
	    .background_(Color.gray(0.13))
	    .front;
	w.view.decorator = FlowLayout(w.view.bounds, 10@10, 8@6);

	// -------- wavetable display --------
	wtView = UserView(w, Rect(0, 0, 620, 200))
	    .background_(Color.black);
	wtView.drawFunc = { |v|
		var pos = currentPos.clip(0, ~wtCount - 1).floor.asInteger;
		var sig = ~wtCache[pos];
		if(sig.notNil, {
			var wpx = v.bounds.width;
			var hpx = v.bounds.height;
			Pen.strokeColor = Color.new(0.4, 1.0, 0.55);
			Pen.width = 1.4;
			sig.do({ |y, i|
				var xp = i / sig.size * wpx;
				var yp = (1 - y) * 0.5 * hpx;
				if (i == 0, { Pen.moveTo(xp @ yp) }, { Pen.lineTo(xp @ yp) });
			});
			Pen.stroke;
			// centerline
			Pen.strokeColor = Color.gray(0.3);
			Pen.line(0 @ (hpx * 0.5), wpx @ (hpx * 0.5));
			Pen.stroke;
			// position label
			Pen.stringAtPoint(
				"pos " ++ pos ++ " / " ++ (~wtCount - 1),
				10 @ 10,
				Font.default,
				Color.gray(0.6));
		});
	};
	updateWT = { AppClock.sched(0, { wtView.refresh; nil }) };

	// -------- bank selector --------
	w.view.decorator.nextLine;
	StaticText(w, 60@22).string_("bank").stringColor_(Color.white);
	~generators.keys.asArray.sort.do({ |k|
		Button(w, 92@22)
			.states_([[k.asString, Color.white, Color.gray(0.32)]])
			.action_({
				~loadBank.(k);
				AppClock.sched(0.4, { updateWT.(); nil });
			});
	});

	// -------- wavetable position --------
	w.view.decorator.nextLine;
	EZSlider(w, 620@34, "wt pos",
		ControlSpec(0, ~wtCount - 3, \lin),
		{ |ez|
			currentPos = ez.value;
			~params[\bufpos] = ez.value;
			~synth.set(\bufpos, ~wtBufs[0].bufnum + ez.value);
			updateWT.();
		}
	).value_(0);

	// -------- freq / detune --------
	w.view.decorator.nextLine;
	EZSlider(w, 305@30, "freq",
		ControlSpec(30, 2000, \exp, 0, 220),
		{ |ez| ~setParam.(\freq, ez.value) }
	);
	EZSlider(w, 305@30, "detune",
		ControlSpec(0, 0.5, \lin, 0, 0.03),
		{ |ez| ~setParam.(\detune, ez.value) }
	);

	// -------- filter --------
	w.view.decorator.nextLine;
	EZSlider(w, 200@30, "cutoff",
		ControlSpec(20, 18000, \exp, 0, 1200),
		{ |ez| ~setParam.(\cutoff, ez.value) }
	);
	EZSlider(w, 200@30, "res",
		ControlSpec(0, 1, \lin, 0, 0.4),
		{ |ez| ~setParam.(\res, ez.value) }
	);
	EZSlider(w, 200@30, "fenv",
		ControlSpec(0, 8000, \lin, 0, 3000),
		{ |ez| ~setParam.(\fenvAmt, ez.value) }
	);

	// -------- amp env --------
	w.view.decorator.nextLine;
	StaticText(w, 620@18).string_("amp env  (a  d  s  r)").stringColor_(Color.gray(0.8));
	w.view.decorator.nextLine;
	[[\atk, 0.01], [\dec, 0.2], [\sus, 0.6], [\rel, 0.5]].do({ |kv|
		var k = kv[0]; var v = kv[1];
		EZSlider(w, 150@28, k.asString,
			ControlSpec(0.001, 2, \exp, 0, v),
			{ |ez| ~setParam.(k, ez.value) }
		);
	});

	// -------- filter env --------
	w.view.decorator.nextLine;
	StaticText(w, 620@18).string_("filt env  (a  d  s  r)").stringColor_(Color.gray(0.8));
	w.view.decorator.nextLine;
	[[\fatk, 0.02], [\fdec, 0.35], [\fsus, 0.4], [\frel, 0.5]].do({ |kv|
		var k = kv[0]; var v = kv[1];
		EZSlider(w, 150@28, k.asString,
			ControlSpec(0.001, 2, \exp, 0, v),
			{ |ez| ~setParam.(k, ez.value) }
		);
	});

	// -------- amp --------
	w.view.decorator.nextLine;
	EZSlider(w, 620@30, "amp",
		ControlSpec(0, 1, \amp, 0, 0.3),
		{ |ez| ~setParam.(\amp, ez.value) }
	);

	// -------- mini keyboard --------
	w.view.decorator.nextLine;
	StaticText(w, 620@18).string_("keys (mouse-hold to sustain)").stringColor_(Color.gray(0.8));
	w.view.decorator.nextLine;
	#[60, 62, 64, 65, 67, 69, 71, 72].do({ |midi|
		var b = Button(w, 74@50);
		b.states_([[midi.asString, Color.white, Color.gray(0.32)]]);
		b.mouseDownAction = { ~synth.set(\freq, midi.midicps, \gate, 1); };
		b.mouseUpAction   = { ~synth.set(\gate, 0); };
	});

	// -------- pattern: transport + rhythm/pitch controls --------
	// Every event reads current UI slider values via ~params (see the
	// STATE + PATTERN block), so tweaking cutoff / bufpos / env in real
	// time reshapes the pattern's next note.
	w.view.decorator.nextLine;
	StaticText(w, 620@18).string_("pattern").stringColor_(Color.gray(0.8));
	w.view.decorator.nextLine;

	Button(w, 90@30)
		.states_([
			["play",  Color.white, Color.new(0.15, 0.45, 0.25)],
			["stop",  Color.white, Color.new(0.5,  0.2,  0.2)]
		])
		.action_({ |b|
			if (b.value == 1,
				{ Pdef(\vosc3wt_pat).play(quant: 0) },
				{ Pdef(\vosc3wt_pat).stop });
		});

	EZSlider(w, 260@30, "pat dur",
		ControlSpec(0.05, 2, \exp, 0, 0.25),
		{ |ez| ~patParams[\dur] = ez.value }
	);

	EZSlider(w, 260@30, "pat root",
		ControlSpec(24, 84, \lin, 1, 48),
		{ |ez| ~patParams[\root] = ez.value.asInteger }
	);

	// Scale selector — one button per scale in ~scales.
	w.view.decorator.nextLine;
	StaticText(w, 60@22).string_("scale").stringColor_(Color.white);
	~scales.keys.asArray.sort.do({ |k|
		Button(w, 82@22)
			.states_([[k.asString, Color.white, Color.gray(0.32)]])
			.action_({ ~patParams[\scale] = k; });
	});

	w.view.decorator.nextLine;
	EZSlider(w, 620@30, "octaves",
		ControlSpec(1, 6, \lin, 1, 3),
		{ |ez| ~patParams[\octaves] = ez.value.asInteger }
	);

	w.onClose = {
		Pdef(\vosc3wt_pat).stop;
		if (~synth.notNil, { ~synth.free; ~synth = nil });
	};
	updateWT.();
	w
};

~ui = ~makeUI.value;
)

// ---------------------------------------------------------------- CLEANUP (evaluate manually)
// ~ui.close;                     // close window (also frees synth via onClose)
// ~wtBufs.do(_.free); ~wtBufs = nil; ~wtCache = nil;   // free the buffer bank
