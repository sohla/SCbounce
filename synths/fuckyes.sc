(
{
		var in = LocalIn.ar(2);
	var frq = 900;//MouseY.kr(10, 1e3, \linear);
	var source = SinOsc.ar(frq,0,0.1);
	var sig = Pluck.ar(source + (in * 0.1), Dust.ar(900), frq.reciprocal, frq.reciprocal, 1,
        coef:0.5)!2;
	sig = sig.softclip.distort;
	sig = HPF.ar(sig, 200);
	sig = LPF.ar(sig, 1.9e4);
	sig = DelayC.ar(sig,0.2,0.2);
	// sig = GVerb.ar(sig);
	LocalOut.ar(sig);
	sig = Mix.ar([sig, source]);
	 sig = PitchShift.ar(sig,0.2, [0.126,0.125]);
	Out.ar(0, BLowShelf.ar(sig, db:-8));
}.play



)

9.2e3




                                                                                                                             /