// SYNTHESIS CONTROL UI - Processing Implementation
// Recreates the HTML blob visualization in Processing

// Canvas dimensions
int canvasWidth = 800;
int canvasHeight = 500;

// Parameters
float brightness = 1.0;
float texture = 0.0;
float thickness = 0.0;
float motion = 0.0;

// Blob system
BlobSystem blobSystem;
float lastTime;

void setup() {
  size(800, 500);
  smooth();

  // Initialize blob system
  blobSystem = new BlobSystem();
  lastTime = millis() / 1000.0;

  println("Synthesis Control UI loaded");
  println("Use keyboard to control parameters:");
  println("1/2: Brightness (Dark/Bright)");
  println("3/4: Texture (Smooth/Textured)");
  println("5/6: Thickness (Thin/Thick)");
  println("7/8: Motion (Still/Moving)");
}

void draw() {
  background(0);

  // Calculate delta time
  float currentTime = millis() / 1000.0;
  float deltaTime = currentTime - lastTime;
  lastTime = currentTime;

  // Update and render blob system
  blobSystem.update(deltaTime);
  blobSystem.render();

  // Draw UI overlay
  drawUI();
}

void drawUI() {
  // Draw semi-transparent overlay at top
  fill(10, 10, 10, 200);
  noStroke();
  rect(0, 0, width, 120);

  // Draw parameter labels
  fill(136, 136, 136);
  textSize(12);
  textAlign(LEFT);

  text("BRIGHTNESS: " + nf(brightness, 0, 2), 20, 25);
  text("TEXTURE: " + nf(texture, 0, 2), 20, 50);
  text("THICKNESS: " + nf(thickness, 0, 2), 20, 75);
  text("MOTION: " + nf(motion, 0, 2), 20, 100);

  // Draw instructions
  fill(85, 85, 85);
  textSize(10);
  text("Keys: 1/2=Brightness, 3/4=Texture, 5/6=Thickness, 7/8=Motion", 20, height - 10);
}

void keyPressed() {
  float step = 0.05;

  // Brightness controls
  if (key == '1') brightness = constrain(brightness - step, 0, 1);
  if (key == '2') brightness = constrain(brightness + step, 0, 1);

  // Texture controls
  if (key == '3') texture = constrain(texture - step, 0, 1);
  if (key == '4') texture = constrain(texture + step, 0, 1);

  // Thickness controls
  if (key == '5') thickness = constrain(thickness - step, 0, 1);
  if (key == '6') thickness = constrain(thickness + step, 0, 1);

  // Motion controls
  if (key == '7') motion = constrain(motion - step, 0, 1);
  if (key == '8') motion = constrain(motion + step, 0, 1);
}

// Blob class - generates animated blob shapes
class Blob {
  float seed;
  int basePoints = 8;
  float time = 0;

  Blob(float s) {
    seed = s;
  }

  ArrayList<PVector> getControlPoints() {
    ArrayList<PVector> points = new ArrayList<PVector>();
    int numPoints = basePoints + floor(texture * 40);

    float motionAmount = 1 - motion;
    float rotationOffset = time * (1 - motionAmount) * 1.0;

    for (int i = 0; i < numPoints; i++) {
      float angle = (float(i) / numPoints) * TWO_PI + rotationOffset;

      float baseRadius = 140;

      float textureFade = pow(texture, 0.5);
      float baseMotion = textureFade * (
        sin(angle * 2 + time * 2) * 15 +
        cos(angle * 3 + time * 1.5) * 10
      );

      // Random displacement
      float seedVal1 = sin(i * 12.9898 + seed * 78.233) * 0.5 + 0.5;
      float seedVal2 = sin(i * 45.164 + seed * 32.234) * 0.5 + 0.5;
      float seedVal3 = sin(i * 91.327 + seed * 15.719) * 0.5 + 0.5;

      float roughRandom = (seedVal1 + seedVal2 * 0.5 + seedVal3 * 0.3) / 1.8;
      float randomDisplacement = roughRandom * texture * 108;

      float radius = baseRadius + baseMotion + randomDisplacement;

      points.add(new PVector(
        width/2 + cos(angle) * radius,
        height/2 + sin(angle) * radius
      ));
    }

    return points;
  }

  void drawPath() {
    ArrayList<PVector> points = getControlPoints();

    if (points.size() < 3) return;

    beginShape();

    if (texture < 0.3) {
      // Smooth - use curve vertices
      for (int i = 0; i < points.size(); i++) {
        PVector p = points.get(i);
        curveVertex(p.x, p.y);
      }
      // Close the curve
      PVector first = points.get(0);
      curveVertex(first.x, first.y);
      PVector second = points.get(1);
      curveVertex(second.x, second.y);
    } else {
      // Textured - use straight lines
      for (int i = 0; i < points.size(); i++) {
        PVector p = points.get(i);
        vertex(p.x, p.y);
      }
    }

    endShape(CLOSE);
  }

  void update(float deltaTime) {
    float motionAmount = 1 - motion;
    time += deltaTime * (1 - motionAmount) * 12.0;
  }
}

// BlobSystem class - manages multiple blob instances
class BlobSystem {
  ArrayList<Blob> blobs;
  int maxBlobs = 350;

  BlobSystem() {
    blobs = new ArrayList<Blob>();
    for (int i = 0; i < maxBlobs; i++) {
      blobs.add(new Blob(i * 0.1));
    }
  }

  color getColor() {
    float value = brightness * 255;
    return color(value, value, value);
  }

  void render() {
    // Calculate how many instances to show based on thickness
    float thicknessAmount = 1 - thickness;
    int numInstances = max(1, floor(1 + (1 - thicknessAmount) * (maxBlobs - 1)));

    color c = getColor();

    // Draw instances from back to front
    for (int i = 0; i < numInstances; i++) {
      Blob blob = blobs.get(i);

      float normalizedIndex = float(i) / max(1, numInstances - 1);

      // Spread calculation
      float spreadAmount = thickness;
      float spreadFactor = spreadAmount * 2.0;
      float spread = pow(normalizedIndex, 1.5) * spreadFactor * 33.25;

      // Alpha calculation
      float alphaThickness = 1 - thickness;
      float baseAlpha = alphaThickness * 0.5 + 0.15;

      float seedVariation = sin(blob.seed * 12.9898 + blob.seed * 78.233) * 0.5 + 0.5;
      float alphaVariation = seedVariation * 0.4 + 0.6;
      float depthFalloff = 1 - (normalizedIndex * 0.3);

      float alpha = baseAlpha * alphaVariation * depthFalloff * 255;

      // Scale and rotation
      float scale = 1 - (normalizedIndex * 0.05);
      float rotationAngle = normalizedIndex * TWO_PI * spreadFactor * 2;

      float motionAmount = 1 - motion;
      float motionRotation = blob.time * (1 - motionAmount) * 0.5;

      float spreadX = cos(rotationAngle) * spread;
      float spreadY = sin(rotationAngle) * spread;

      // Apply transformations
      pushMatrix();

      translate(width/2 + spreadX, height/2 + spreadY);
      rotate(motionRotation);
      scale(scale);
      translate(-width/2, -height/2);

      // Draw blob
      stroke(red(c), green(c), blue(c), alpha);
      strokeWeight(2);
      noFill();
      blob.drawPath();

      popMatrix();
    }
  }

  void update(float deltaTime) {
    for (Blob blob : blobs) {
      blob.update(deltaTime);
    }
  }
}
