(
x = {
    var in = SoundIn.ar(0);
    PitchShiftPA.ar(
        in,
        Lag.kr(Pitch.kr(in)[0],0.2), //pitch tracking - we take just the frequency
        1,//MouseX.kr(0.5, 2), //pitchRatio
        MouseY.kr(0.5, 2), //formantRatio
    )!2
}.play
)
x.free;

Quarks.gui

d=Buffer.read(s,PathName.new("~/Music/SCSamples/inMemoryKidsStories2.wav").asAbsolutePath);
d=Buffer.read(s,PathName.new("~/Music/SCSamples/lassem_ria2.wav").asAbsolutePath);
d=Buffer.read(s,PathName.new("~/Music/SCSamples/lassem_anton.wav").asAbsolutePath);


(
{
 
   var in, fft, output;

    in=PlayBuf.ar(1,d,BufRateScale.kr(d),1,0,1);

    output=PitchShiftPA.ar(
        in,
        Lag.kr(Pitch.kr(in)[0],0.5), //pitch tracking - we take just the frequency
        MouseX.kr(0.5, 2), //pitchRatio
        MouseY.kr(0.5, 2), //formantRatio
    )!2;

    Out.ar(0,Pan2.ar(output[0]));
}.play
)



(
{

    var in, output;

    in=PlayBuf.ar(1,d,BufRateScale.kr(d),1,0,1);

    output=SMS.ar(in, 80,80, 8.0,0.2, MouseX.kr(0.5,2), 0, formantpreserve:0 );

    Out.ar(0,Pan2.ar(output[0]));
}.play
)



//Quaternion.new(0.1,0.2,0.3,0.4).asEuler

ArduinoQuaternion