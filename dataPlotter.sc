
(
FileDialog({ |paths|

	var file = File(paths[0], "r");
	var strToFloats = file.readAllString.split($, ).asFloat;
	var data = strToFloats.reshape(strToFloats.size,2);
	var values, runningAvg, movingAvg;
	var window = 20;   // sliding-window size in frames
	var colors = [Color.gray, Color.blue, Color.green(0.5)];
	var labels;

	data = data.reshape(data.size.half.asInteger,2);
	data = data.lace(data.size * 2);
	data = data.reshape(2,data.size.half.asInteger);

	// derived series from the y-values (data[1])
	values     = data[1];
	runningAvg = values.integrate / (1..values.size);       // cumulative mean
	movingAvg  = values.size.collect({ |i|                   // sliding-window mean
		var lo = max(0, i - window + 1);
		values[lo..i].mean;
	});

	a = [values, runningAvg, movingAvg].plot;
	a.superpose = true;   // overlay all three on one set of axes

	a.plotMode = \linear;   // connected lines, no per-point dots (\plines clutters at high counts)
	a.minval_(0);
	a.maxval_(0.06);
	a.setProperties(
		\labelX, "Frames",
		\labelY, "ms",
	);
	a.plotColor = colors;
	a.refresh;

	// legend: color-matched labels overlaid on the plot window
	labels = ["raw", "running avg", "moving avg (" ++ window ++ ")"];
	labels.do({ |str, i|
		StaticText(a.parent, Rect(60 + (i * 130), 8, 125, 18))
			.string_(str)
			.stringColor_(colors[i])
			.background_(Color.white.alpha_(0.6))
			.font_(Font.default.boldVariant);
	});

	data[0].size.postln;


}, {
    postln("Cancelled. Try again.");
});
)
