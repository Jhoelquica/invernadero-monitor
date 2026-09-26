/*
  Invernadero Monitor - Arduino Uno + HC-05 + DHT11 + LED (PWM)

  Conexiones:
    HC-05 TXD -> pin 10 (RX de SoftwareSerial)
    HC-05 RXD -> pin 11 (TX de SoftwareSerial) mediante divisor de tension 5V -> 3.3V
    DHT11 DATA -> pin 2
    LED (con resistencia 220 ohm) -> pin 9 (PWM)

  Libreria requerida: "DHT sensor library" de Adafruit (y "Adafruit Unified Sensor").

  Protocolo (lineas de texto terminadas en \n, 9600 baudios):
    Arduino -> app:
      T:24.5,H:60,L:1,M:0,B:255,S:30   lectura periodica cada 5 s
      L:1,M:0,B:128,S:30               eco de estado tras un comando
      E:DHT                            fallo del sensor
      PONG                             respuesta a PING
    App -> Arduino:
      LED:1 | LED:0 | BRI:0-255 | AUTO:1 | AUTO:0 | SET:10-60 | PING
*/

#include <SoftwareSerial.h>
#include <DHT.h>

const uint8_t PIN_BT_RX = 10;
const uint8_t PIN_BT_TX = 11;
const uint8_t PIN_DHT = 2;
const uint8_t PIN_LED = 9;

const unsigned long SEND_INTERVAL_MS = 5000;
const float HYSTERESIS_C = 1.0;
const uint8_t LINE_BUFFER_SIZE = 32;

SoftwareSerial bt(PIN_BT_RX, PIN_BT_TX);
DHT dht(PIN_DHT, DHT11);

bool ledOn = false;
bool autoMode = false;
uint8_t brightness = 255;
int threshold = 30;

float lastTemperature = NAN;
unsigned long lastSend = 0;

char lineBuffer[LINE_BUFFER_SIZE];
uint8_t lineLength = 0;

void applyLed() {
  analogWrite(PIN_LED, ledOn ? brightness : 0);
}

void sendState(bool includeReading, float temperature, float humidity) {
  if (includeReading) {
    bt.print(F("T:"));
    bt.print(temperature, 1);
    bt.print(F(",H:"));
    bt.print(humidity, 0);
    bt.print(',');
  }
  bt.print(F("L:"));
  bt.print(ledOn ? 1 : 0);
  bt.print(F(",M:"));
  bt.print(autoMode ? 1 : 0);
  bt.print(F(",B:"));
  bt.print(brightness);
  bt.print(F(",S:"));
  bt.println(threshold);
}

void handleCommand(const char* line) {
  if (strcmp(line, "PING") == 0) {
    bt.println(F("PONG"));
    return;
  }
  if (strncmp(line, "LED:", 4) == 0) {
    ledOn = (line[4] == '1');
    applyLed();
  } else if (strncmp(line, "BRI:", 4) == 0) {
    brightness = (uint8_t)constrain(atoi(line + 4), 0, 255);
    applyLed();
  } else if (strncmp(line, "AUTO:", 5) == 0) {
    autoMode = (line[5] == '1');
    if (autoMode) updateAuto();
  } else if (strncmp(line, "SET:", 4) == 0) {
    threshold = constrain(atoi(line + 4), 10, 60);
    if (autoMode) updateAuto();
  } else {
    return;
  }
  sendState(false, 0, 0);
}

void updateAuto() {
  if (isnan(lastTemperature)) return;
  if (lastTemperature > threshold) {
    ledOn = true;
  } else if (lastTemperature < threshold - HYSTERESIS_C) {
    ledOn = false;
  }
  applyLed();
}

void readSerial() {
  while (bt.available()) {
    char c = (char)bt.read();
    if (c == '\n') {
      lineBuffer[lineLength] = '\0';
      if (lineLength > 0) handleCommand(lineBuffer);
      lineLength = 0;
    } else if (c != '\r') {
      if (lineLength < LINE_BUFFER_SIZE - 1) {
        lineBuffer[lineLength++] = c;
      } else {
        lineLength = 0;
      }
    }
  }
}

void setup() {
  pinMode(PIN_LED, OUTPUT);
  applyLed();
  bt.begin(9600);
  dht.begin();
}

void loop() {
  readSerial();

  unsigned long now = millis();
  if (now - lastSend >= SEND_INTERVAL_MS) {
    lastSend = now;
    float temperature = dht.readTemperature();
    float humidity = dht.readHumidity();
    if (isnan(temperature) || isnan(humidity)) {
      bt.println(F("E:DHT"));
    } else {
      lastTemperature = temperature;
      if (autoMode) updateAuto();
      sendState(true, temperature, humidity);
    }
  }
}
