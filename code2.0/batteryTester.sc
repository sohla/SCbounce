
(
	var w = Window(bounds:Rect(0,0,1200,400));
	var plot = Plotter(\plotter, w.bounds, w).value_([0,0]);
	var d = Date.localtime;
	var file = File(("~/batteryTest_"++d.day++"_"++d.month++"_"++d.year++"_"++d.hour++"_"++d.minute++".text").standardizePath,"w");

	w.layout =  v;
	w.front;
	w.alwaysOnTop = true;

	o = OSCFunc({ |msg, time, addr, recvPort|
		var m = Date.localtime.rawSeconds.asString++" : " ++ msg.asString;

		{ file.write(m++"\n") }.defer;
		m.postln;

	{
		plot.value = plot.value.flop;
		plot.value = plot.value.add([msg[1], msg[2]]);
		plot.value = plot.value.flop;

	}.defer;
	},'/60:01:E2:E2:27:48/Battery');

	CmdPeriod.add({
		w.close;
		file.close;
		o.free;
	});

)




(
	var w = Window(bounds:Rect(0,0,1200,400));
	var plot = Plotter(\plotter, w.bounds, w).value_([0,0]);
	var d = Date.localtime;
	var file = File(("~/batteryTest_22_8_2024_14_26.text").standardizePath,"r");
	var volts = 0, percs = 0;
	var line = file.getLine(1024);
	var values = [];
	var labelAnchors = [\topLeft, \top, \bottomLeft];
	while({line != nil},{
		volts = line.split($,)[1].replace("]","");
		percs= line.split($,)[2].replace("]","");
		line = file.getLine(1024);
		values = values.add([volts.asFloat, percs.asFloat]);
	});


	plot.value = plot.value.flop;
	plot.value = values;
	plot.value = plot.value.flop;
plot.plotMode_(\plines);

	plot.axisLabelX_(["frames (10ms)","frames (10ms)"]);
	plot.axisLabelY_(["volts","percentage"]);
plot.plotColor_([Color.green,Color.red]);
	plot.refresh;

	file.close;
	w.layout =  v;
	w.front;
	w.alwaysOnTop = true;

	CmdPeriod.add({
		w.close;
	});

)






