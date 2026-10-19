/*
  Invernadero Monitor - Arduino Uno + HC-05 + DHT22/DHT11 + LED (PWM) + riego

  Conexiones:
    HC-05 TXD -> pin 10 (RX de SoftwareSerial)
    HC-05 RXD -> pin 11 (TX de SoftwareSerial) mediante divisor de tension 5V -> 3.3V
    DHT22/DHT11 DATA -> pin 2
    LED (con resistencia 220 ohm) -> pin 9 (PWM)
    Sensor de humedad de suelo capacitivo (AOUT) -> A0
    Modulo rele (IN) -> pin 7; la bomba o electrovalvula se alimenta aparte, a traves del rele

  Libreria requerida: "DHT sensor library" de Adafruit (y "Adafruit Unified Sensor").

  Protocolo (lineas de texto terminadas en \n, 9600 baudios):
    Arduino -> app:
      T:24.5,H:60,L:1,M:0,B:255,S:30,W:45,P:0,R:1,U:35   lectura periodica cada 5 s
      L:1,M:0,B:128,S:30,W:45,P:0,R:1,U:35               eco de estado tras un comando
      ...,Z:1                                            se agrega en modo simulacion de suelo
      EV:1,12,33,46,U    fin de un riego: automatico (1) o manual (0), duracion en s,
                         humedad del suelo al inicio y al final, y motivo:
                         U umbral alcanzado, T tiempo maximo, M detenido, E falla del sensor
      E:DHT | E:SUELO    fallo del sensor
      PONG               respuesta a PING
    App -> Arduino:
      LED:1 | LED:0 | BRI:0-255 | AUTO:1 | AUTO:0 | SET:10-60 | PING
      PUMP:1 | PUMP:0 | RIEGO:1 | RIEGO:0 | SOIL:10-80 | FALLA:1 | FALLA:0 (solo simulacion)
      (no distingue mayusculas de minusculas)

  Depuracion: todo lo enviado por Bluetooth se copia al Serial Monitor (USB, 9600),
  y los comandos recibidos se muestran con el prefijo "RX:".
*/

#include <SoftwareSerial.h>
#include <DHT.h>

const uint8_t PIN_BT_RX = 10;
const uint8_t PIN_BT_TX = 11;
const uint8_t PIN_DHT = 2;
const uint8_t PIN_LED = 9;
const uint8_t PIN_SOIL = A0;
const uint8_t PIN_PUMP = 7;

const unsigned long SEND_INTERVAL_MS = 5000;
const float HYSTERESIS_C = 1.0;
const uint8_t LINE_BUFFER_SIZE = 32;

// Modelo del sensor: DHT22 (AM2302, cuerpo blanco) o DHT11 (cuerpo azul).
// Si se elige el modelo equivocado, las lecturas salen absurdas (ej. T:0.1,H:12).
#define DHT_TYPE DHT22

// Rango de especificacion del sensor: fuera de el, la lectura se considera invalida.
#if DHT_TYPE == DHT11
const float DHT_MIN_TEMP_C = 0.0;
const float DHT_MAX_TEMP_C = 50.0;
const float DHT_MIN_HUMIDITY = 20.0;
const float DHT_MAX_HUMIDITY = 90.0;
#else
const float DHT_MIN_TEMP_C = -40.0;
const float DHT_MAX_TEMP_C = 80.0;
const float DHT_MIN_HUMIDITY = 0.0;
const float DHT_MAX_HUMIDITY = 100.0;
#endif

// 1 = todavia no hay sensor de suelo ni bomba: la humedad del suelo se simula y la app
// lo muestra como "Simulacion". Poner en 0 al conectar el hardware real.
#define SIMULAR_SUELO 1

// La mayoria de modulos rele se activan con nivel bajo en IN.
const bool RELAY_ACTIVE_LOW = true;

// Calibracion del sensor capacitivo: lectura de A0 en el aire (seco) y sumergido en agua.
const int SOIL_RAW_DRY = 520;
const int SOIL_RAW_WET = 260;

const unsigned long IRRIGATION_TICK_MS = 1000;
const uint8_t SOIL_HYSTERESIS = 10;         // el riego automatico termina en umbral + 10 %
const unsigned long PUMP_MAX_MS = 30000;    // seguridad: ningun riego dura mas de 30 s
const unsigned long PUMP_PAUSE_MS = 60000;  // espera minima entre riegos automaticos

SoftwareSerial bt(PIN_BT_RX, PIN_BT_TX);
DHT dht(PIN_DHT, DHT_TYPE);

// Todo lo que se envia por Bluetooth se copia al USB (Serial Monitor, 9600) para depurar.
class Tee : public Print {
 public:
  size_t write(uint8_t c) override {
    Serial.write(c);
    return bt.write(c);
  }
};
Tee out;

bool ledOn = false;
bool autoMode = false;
uint8_t brightness = 255;
int threshold = 30;

float lastTemperature = NAN;
unsigned long lastSend = 0;

bool irrigationAuto = false;
bool pumpOn = false;
bool pumpManual = false;
bool pumpEverStopped = false;
uint8_t soilThreshold = 35;
int soilMoisture = -1;  // -1 = sin lectura valida
int soilAtStart = 0;
unsigned long pumpStartMs = 0;
unsigned long lastPumpStopMs = 0;
unsigned long lastIrrigationTick = 0;

#if SIMULAR_SUELO
float simulatedSoil = 50.0;
bool simulatedFault = false;  // la bomba se enciende pero no moja el suelo
#endif

char lineBuffer[LINE_BUFFER_SIZE];
uint8_t lineLength = 0;

void applyLed() {
  analogWrite(PIN_LED, ledOn ? brightness : 0);
}

void sendState(bool includeReading, float temperature, float humidity) {
  if (includeReading) {
    out.print(F("T:"));
    out.print(temperature, 1);
    out.print(F(",H:"));
    out.print(humidity, 0);
    out.print(',');
  }
  out.print(F("L:"));
  out.print(ledOn ? 1 : 0);
  out.print(F(",M:"));
  out.print(autoMode ? 1 : 0);
  out.print(F(",B:"));
  out.print(brightness);
  out.print(F(",S:"));
  out.print(threshold);
  if (soilMoisture >= 0) {
    out.print(F(",W:"));
    out.print(soilMoisture);
  }
  out.print(F(",P:"));
  out.print(pumpOn ? 1 : 0);
  out.print(F(",R:"));
  out.print(irrigationAuto ? 1 : 0);
  out.print(F(",U:"));
  out.print(soilThreshold);
#if SIMULAR_SUELO
  out.print(F(",Z:1"));
#endif
  out.println();
}

// ---------- Riego ----------

void setPump(bool on) {
  digitalWrite(PIN_PUMP, (on != RELAY_ACTIVE_LOW) ? HIGH : LOW);
}

int readSoil() {
#if SIMULAR_SUELO
  return (int)(simulatedSoil + 0.5);
#else
  int raw = analogRead(PIN_SOIL);
  if (raw < 50 || raw > 1000) return -1;  // sensor desconectado o en corto
  long percent = map(raw, SOIL_RAW_DRY, SOIL_RAW_WET, 0, 100);
  return (int)constrain(percent, 0, 100);
#endif
}

#if SIMULAR_SUELO
// Modelo simple y acelerado para demostraciones: el suelo se seca medio punto por segundo
// y el riego lo humedece tres puntos por segundo, salvo que se simule una falla de la bomba.
void updateSimulatedSoil() {
  if (pumpOn && !simulatedFault) {
    simulatedSoil += 3.0;
  } else {
    simulatedSoil -= 0.5;
  }
  simulatedSoil = constrain(simulatedSoil, 0.0, 100.0);
}
#endif

void startPump(bool manual) {
  if (pumpOn) return;
  pumpOn = true;
  pumpManual = manual;
  pumpStartMs = millis();
  soilAtStart = soilMoisture < 0 ? 0 : soilMoisture;
  setPump(true);
}

// Apaga la bomba e informa el riego a la app para que lo verifique.
void stopPump(char reason) {
  if (!pumpOn) return;
  pumpOn = false;
  setPump(false);
  unsigned long now = millis();
  lastPumpStopMs = now;
  pumpEverStopped = true;
  if (soilMoisture < 0) reason = 'E';
  out.print(F("EV:"));
  out.print(pumpManual ? 0 : 1);
  out.print(',');
  out.print((now - pumpStartMs + 500) / 1000);
  out.print(',');
  out.print(soilAtStart);
  out.print(',');
  out.print(soilMoisture < 0 ? 0 : soilMoisture);
  out.print(',');
  out.println(reason);
}

void updateIrrigation() {
#if SIMULAR_SUELO
  updateSimulatedSoil();
#endif
  soilMoisture = readSoil();
  unsigned long now = millis();
  bool wasOn = pumpOn;

  if (pumpOn) {
    if (soilMoisture < 0) {
      stopPump('E');
    } else if (now - pumpStartMs >= PUMP_MAX_MS) {
      stopPump('T');
    } else if (!pumpManual && soilMoisture >= soilThreshold + SOIL_HYSTERESIS) {
      stopPump('U');
    }
  } else if (irrigationAuto && soilMoisture >= 0 && soilMoisture < soilThreshold) {
    bool rested = !pumpEverStopped || now - lastPumpStopMs >= PUMP_PAUSE_MS;
    if (rested) startPump(false);
  }

  if (pumpOn != wasOn) sendState(false, 0, 0);
}

// ---------- Comandos ----------

void handleCommand(const char* line) {
  Serial.print(F("RX: "));
  Serial.println(line);
  if (strcmp(line, "PING") == 0) {
    out.println(F("PONG"));
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
  } else if (strncmp(line, "PUMP:", 5) == 0) {
    if (line[5] == '1') {
      startPump(true);
    } else {
      stopPump('M');
    }
  } else if (strncmp(line, "RIEGO:", 6) == 0) {
    irrigationAuto = (line[6] == '1');
    if (!irrigationAuto && pumpOn && !pumpManual) stopPump('M');
  } else if (strncmp(line, "SOIL:", 5) == 0) {
    soilThreshold = (uint8_t)constrain(atoi(line + 5), 10, 80);
#if SIMULAR_SUELO
  } else if (strncmp(line, "FALLA:", 6) == 0) {
    simulatedFault = (line[6] == '1');
#endif
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
    char c = (char)toupper(bt.read());
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
  pinMode(PIN_PUMP, OUTPUT);
  setPump(false);
  pinMode(PIN_LED, OUTPUT);
  applyLed();
  Serial.begin(9600);
  bt.begin(9600);
  dht.begin();
}

void loop() {
  readSerial();

  unsigned long now = millis();
  if (now - lastIrrigationTick >= IRRIGATION_TICK_MS) {
    lastIrrigationTick = now;
    updateIrrigation();
  }

  if (now - lastSend >= SEND_INTERVAL_MS) {
    lastSend = now;
    float temperature = dht.readTemperature();
    float humidity = dht.readHumidity();
    bool valid = !isnan(temperature) && !isnan(humidity) &&
                 temperature >= DHT_MIN_TEMP_C && temperature <= DHT_MAX_TEMP_C &&
                 humidity >= DHT_MIN_HUMIDITY && humidity <= DHT_MAX_HUMIDITY;
    if (soilMoisture < 0) out.println(F("E:SUELO"));
    if (!valid) {
      out.println(F("E:DHT"));
      sendState(false, 0, 0);
    } else {
      lastTemperature = temperature;
      if (autoMode) updateAuto();
      sendState(true, temperature, humidity);
    }
  }
}
