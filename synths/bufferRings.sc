~bufnum = Buffer.read(s, "/Users/soh_la/Downloads/alessioSamples/andTheDrums.wav"); // remember to free

(
// change freqs and ringtimes with mouse
{
	var mx = MouseX.kr(1,2);
	var buf = LocalBuf.newFrom([45,52,61].midicps);
	var freqs = [mx,mx*4.midiratio,mx*12.midiratio];
	var sig = PlayBuf.ar(1, ~bufnum, BufRateScale.kr(~bufnum), loop:true);
	var amp = Amplitude.ar(sig,0.1,50);
	var gate= Amplitude.ar(sig,0.01,1);
	var spl = Splay.arFill(4,{|i|
		CombL.ar(sig, 0.2, WrapIndex.kr(buf,i).reciprocal / mx, amp * 10, gate * 1)
	});
	spl+sig

}.play;
)

(
// change freqs and ringtimes with mouse
{
	var mx = MouseX.kr(0.5,1);
	var buf = LocalBuf.newFrom([0,7,16].midiratio);
	var freqs = [mx,mx*4.midiratio,mx*12.midiratio];
	var sig = PlayBuf.ar(1, ~bufnum, BufRateScale.kr(~bufnum), loop:true);
	var amp = Amplitude.ar(sig,0.1,2);
	var gate= Amplitude.ar(sig,0.01,0.01);
	var spl = Splay.arFill(3,{|i|
		PitchShift.ar(sig,0.07,WrapIndex.kr(buf,i) * mx,0.0,0.03,3)
	});
	spl+sig

}.play;
)

