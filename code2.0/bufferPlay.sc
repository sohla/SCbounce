Buffer.freeAll;
b = Buffer.alloc(s, s.sampleRate * 2); //two second buffer




(
    //record into buffer, overwriting old contents, and also play the buffer
    x = {
        var sig;
        sig = SinOsc.ar(LFNoise2.ar(4).range(50,100), 0, 0.1);
        RecordBuf.ar(sig, b, \offset.kr(0), \reclev.kr(1), \prelev.kr(0), \run.kr(1), \loop.kr(1));
        PlayBuf.ar(1!2, b, loop:1);
    }.play;
)
    
y = {PlayBuf.ar(1!2, b, loop:1)}.play;
y.free
b.plot
b.getToFloatArray(wait:0.01, action:{|a| "done".postln; });
b.free

s.boot;
b = Buffer.read(s, Platform.resourceDir +/+ "sounds/a11wlk01.wav");
// like Buffer.plot
b.getToFloatArray(wait:0.01,action:{arg array; a = array; { a.plot }.defer; "done".postln });
b.free;





"/myapp/instrument/cntl",1,3,64
"/myapp/instrument/cntl",1,3,0
 
// design supercollider sythn with lfo amp modulation
// design supercollider synth with lfo freq modulation


