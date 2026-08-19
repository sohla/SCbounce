# airviz - GLES2 visual renderer for AirKit
#
# Pi 5 / Bookworm:
#   sudo apt install libegl1-mesa-dev libgles2-mesa-dev libx11-dev
#   make && ./airviz -v
#
# -ffast-math is deliberate: the whole inner loop is sinf/cosf/atan2f over
# point arrays and none of it needs IEEE edge-case behaviour.

CC      ?= cc
CFLAGS  ?= -O2 -ffast-math -Wall -Wextra -Wno-unused-parameter
LDLIBS   = -lEGL -lGLESv2 -lX11 -lm

# Cortex-A76 on the Pi 5. Comment out when cross-building.
ARCH    ?= -mcpu=native

OBJ = main.o gfx.o shapes.o

airviz: $(OBJ)
	$(CC) $(CFLAGS) $(ARCH) -o $@ $(OBJ) $(LDLIBS)

# No GL, no X, no display. Builds anywhere - including macOS - so the wire
# format and the SC sender can be tested away from the Pi:
#     make airviz-headless && ./airviz-headless -n -v
airviz-headless: main.c gfx_null.c shapes.c airviz.h osc.h
	$(CC) $(CFLAGS) -o $@ main.c gfx_null.c shapes.c -lm

%.o: %.c airviz.h
	$(CC) $(CFLAGS) $(ARCH) -c -o $@ $<

main.o: osc.h

clean:
	rm -f $(OBJ) airviz airviz-headless

.PHONY: clean
