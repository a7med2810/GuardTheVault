const int JOY_X = A0;
const int JOY_Y = A1;
const int START_BUTTON = 3;
const int ACTION_BUTTON = 4;

const int RED_PIN = 9;
const int GREEN_PIN = 10;
const int BLUE_PIN = 11;

void setup() {
  Serial.begin(9600);

  pinMode(START_BUTTON, INPUT_PULLUP);
  pinMode(ACTION_BUTTON, INPUT_PULLUP);

  pinMode(RED_PIN, OUTPUT);
  pinMode(GREEN_PIN, OUTPUT);
  pinMode(BLUE_PIN, OUTPUT);

  // Keep vibration disabled.
  digitalWrite(5, LOW);
  pinMode(5, OUTPUT);
}

void loop() {
  // Cycle red, green, blue: one second each.
  // Receive colour commands from Java without blocking input.
while (Serial.available() > 0) {
  char command = Serial.read();

  if (command == 'R' || command == 'G'
      || command == 'B' || command == 'O') {
    digitalWrite(RED_PIN, command == 'R');
    digitalWrite(GREEN_PIN, command == 'G');
    digitalWrite(BLUE_PIN, command == 'B');
  }
}

  // Preserve the game's input message format.
  Serial.print("I,");
  Serial.print(analogRead(JOY_X));
  Serial.print(",");
  Serial.print(analogRead(JOY_Y));
  Serial.print(",");
  Serial.print(digitalRead(START_BUTTON) == LOW);
  Serial.print(",");
  Serial.println(digitalRead(ACTION_BUTTON) == LOW);

  delay(30);
}