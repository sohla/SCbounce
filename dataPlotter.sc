
(
var loadAndPlot;   // forward declaration so the function can re-invoke itself

loadAndPlot = {
	FileDialog({ |paths|

		var file = File(paths[0], "r");
		var strToFloats = file.readAllString.split($, ).asFloat;
		var data = strToFloats.reshape(strToFloats.size,2);
		var values, runningAvg, movingAvg;
		var window = 20;   // sliding-window size in frames
		var settle = 2;    // drop the first N frames while the system settles
		var colors = [Color.gray, Color.blue, Color.green(0.5)];
		var labels;
		var prevOnClose;

		data = data.reshape(data.size.half.asInteger,2);
		data = data.lace(data.size * 2);
		data = data.reshape(2,data.size.half.asInteger);

		// derived series from the y-values (data[1]), dropping the settle frames
		values     = data[1].drop(settle);
		runningAvg = values.integrate / (1..values.size);       // cumulative mean
		movingAvg  = values.size.collect({ |i|                   // sliding-window mean
			var lo = max(0, i - window + 1);
			values[lo..i].mean;
		});

		a = [values, runningAvg, movingAvg].plot(bounds: Rect(100, 100, 1400, 800));
		a.superpose = true;

		a.plotMode = \linear;
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

		// when this plot window closes, run the Plotter's own cleanup then
		// re-open the file dialog so we can pick & recalculate fresh data
		prevOnClose = a.parent.onClose;
		a.parent.onClose = { |win|
			prevOnClose.value(win);
			loadAndPlot.value;
		};

	}, {
		postln("Cancelled. Try again.");
	});
};

loadAndPlot.value;
)
