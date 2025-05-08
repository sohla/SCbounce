var addTitleView = { |d|
	StaticText()
        .stringColor_(d.color)
        .font_(Font(size:64))
        .minWidth_(100)
        .maxHeight_(80)
        .string_(d.name)
};
addTitleView

