var plotterView = {|data, parentView|
	// var pv = UserView(parentView, parentView.bounds).backColor_(data.color.alpha_(0.5)).animate_(true).frameRate_(12);
	var plotter = Plotter("plotter", parentView.bounds, parentView)
		.value_((0..50))
		.minval_(-1.0)
		.maxval_(1.0)
		.refresh;
	var pd = [];
	var cols = [Color.yellow,Color.magenta,Color.cyan,Color.red,Color.green,Color.blue];
	var plotData = { data.env.use{ ~plot.(data)} };
	var pmin = data.env.use{ ~plotMin.()};
	var pmax = data.env.use{ ~plotMax.()};

	plotter.setProperties(
		\gridColorX, Color.grey,
		\gridColorY, Color.grey,
		\plotColor, cols,
		\fontColor, data.color,
		\backgroundColor, Color.black
	);


	Routine{
		loop {
			{
			// pd = pd.addFirst([sin(Clock.beats),cos(Clock.beats),tan(Clock.beats).wrap2(1.0)]);
			pd = pd.addFirst(plotData.());

			if(pd.size > 50, {pd.pop()});
			plotter.superpose = true;
			plotter.setValue(pd, false, true, false);
			plotter.value = plotter.value.keep(50).flop;

			plotter.minval_(pmin);
			plotter.maxval_(pmax);
			// plotter.setProperties(\plotColor, col).refresh;

			}.defer;
			0.03.yield; //this has a big impact on CPU use
		}
	}.play;

	plotter.interactionView.mouseDownAction_({false});
	plotter.interactionView.mouseUpAction_({false});

	plotter.interactionView
};


plotterView