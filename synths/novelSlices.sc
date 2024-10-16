// ~src = Buffer.read(s,FluidFilesPath("Nicol-LoopE-M.wav"));
~src = Buffer.read(s,"/Volumes/2024/yourDNA/yourDNASamples/HK\ laughing2-glued.wav");

(
~indices = Buffer(s);
FluidBufNoveltySlice.processBlocking(
	s,
	~src,
	indices:~indices,
	algorithm: 1,
	threshold: 0.47,
	filterSize: 1,
	minSliceLength: 10,

);
FluidWaveform(~src,~indices,bounds:Rect(0,0,1600,400));
)

//loops over a slice from onset to the next slice point using MouseX to choose which
(
{
    var gate_index = MouseX.kr(0,~indices.numFrames-1).poll(label:"slice index");
    var start = BufRd.kr(1,~indices,gate_index,1,1);
    var end = BufRd.kr(1,~indices,gate_index+1,1,1);
    var phs = Phasor.ar(0,BufRateScale.ir(~src),start,end);
    BufRd.ar(1,~src,phs,1,4).dup;
}.play;
)

// instead of the raw spectrum (algorithm:0), we'll try mfccs (algorithm:1):
(
~indices = Buffer(s);
FluidBufNoveltySlice.processBlocking(s,~src,algorithm:1,threshold:0.5,kernelSize:5,filterSize:5,indices:~indices,minSliceLength:5);
FluidWaveform(~src,~indices,bounds:Rect(0,0,1600,400));
)