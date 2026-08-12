//------------------------------------------------------------
// visualView.scd
//
// The visual tab. It is now built ALWAYS, whether or not a second screen
// is attached - what changes is only what it holds.
//
// This returns an empty host View, not a surface. There is exactly one
// surface in the app and it lives either here or in the HDMI window;
// visualCore.moveSurface(host) is what puts it in one or the other, and
// main.sc drops a placeholder into whichever host is not currently
// holding it.
//
// Building the tab unconditionally is what makes the surface movable at
// runtime : the tab set and the StackLayout never change shape, so an
// HDMI cable going in or out only re-parents the surface instead of
// forcing the whole UI to be rebuilt.
//
// The surface itself is a StackLayout whose page 0 is the \main overlay
// canvas and page 1 is the per-device grid. Switch between them with
// /airkit/visualScene overlay|grid, or the scene button on the system
// tab. Switch which SCREEN it is on with /airkit/visualTarget tab|hdmi.
//------------------------------------------------------------

{ View().background_(Color.black) };
