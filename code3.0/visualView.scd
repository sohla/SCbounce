//------------------------------------------------------------
// visualView.scd
//
// The visual tab. Only built when there is NO second screen - when an
// HDMI display is attached the same surface goes into its own window on
// that output instead (see main.sc).
//
// It is literally the same surface either way: a StackLayout whose
// page 0 is the \main overlay canvas and page 1 is the per-device grid.
// Switch between them with /airkit/visualScene overlay|grid, or the
// scene button on the system tab.
//------------------------------------------------------------

var visualCore = Require("visualCore.scd");

{ visualCore.makeSurface() };
