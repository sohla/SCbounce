(
var func = {|me|
	var d = Date.getDate.rawSeconds.asInteger.postln;
    Pen.use{
        10.do{
            Color.red(rrand(0.0, 1), rrand(0.0, 0.5)).set;
            Pen.addArc((400.exprand(2))@(200.rand), rrand(10, 200), 2pi.rand, pi);
            Pen.perform([\stroke, \fill].choose);
        }
    }
};

var makeButton = {|func|
	UserView()
	.background_(Color.black.alpha_(0.8))
	.mouseDownAction_({|m|func.(); m.backColor_(Color.new255(0, 139, 69))})
	.mouseUpAction_({|m|func.(); m.backColor_(Color.black.alpha_(0.8))})
	// .drawFunc_(func)
	.animate_(true)
	.frameRate_(60)
};

var spacing = 4;

var grid = {
	View().layout_(GridLayout.rows(
		[
			makeButton.({"do 0".postln}),
			makeButton.({"do 1".postln}),
			makeButton.({"do 2".postln}),
		],
		[
			makeButton.({"do 3".postln}),
			makeButton.({"do 4".postln}),
			makeButton.({"do 5".postln}),
		],
	).hSpacing_(spacing).vSpacing_(spacing))//.margins_([spacing,spacing,spacing,spacing])
};

var stack =
	StackLayout(
		grid.(),
		TextView().string_("Hello"),
		TextView().string_("World"),
	);


var tabButton = {|title|
	Button()
	.minHeight_(100)
	.font_(Font(size:30))
	.states_([
		[title, Color.white, Color.grey],
		[title, Color.white, Color.new255(0, 139, 69)]
	])
	.action_({|but|

		if(but.value == 1,{

			but.parent.children.do({|ab,i|
				if(but != ab, {
					ab.valueAction_(0)
				},{
					i.postln;
					stack.index = i;
				});
			});
		});
	})
};


var tab1;
var tabs = {|t|
	View().layout_(HLayout(
		tab1 = tabButton.("🁪"),
		tabButton.("⚃"),
		tabButton.("※")
	).spacing_(0).margins_(0)).maxHeight_(100);

};


w = Window().bounds_(Rect(100,100,1000,700)).layout_(
	VLayout(
		tabs.(),
		stack.()

	);
).front;
tab1.valueAction = 1;
)