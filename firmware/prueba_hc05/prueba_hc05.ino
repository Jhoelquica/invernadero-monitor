/*
  Prueba minima del HC-05 (sin sensor ni LED externo).

  Conexiones: HC-05 TXD -> pin 10, HC-05 RXD -> pin 11 (con divisor 1k/2k),
              VCC -> 5V, GND -> GND.

  Que hace:
    - Cada segundo envia "HOLA" por Bluetooth (se debe ver en el terminal del celular).
    - Cada vez que recibe un byte del celular, el LED integrado (pin 13) cambia de estado
      y el Arduino responde "ECO:<caracter>".
    - Todo se copia al Serial Monitor (USB, 9600) con prefijos TX: y RX:.

  Interpretacion:
    - Celular ve "HOLA" cada segundo   -> la salida (pin 11 -> RXD) funciona.
    - El LED 13 cambia al escribir algo -> la entrada (TXD -> pin 10) funciona.
*/

#include <SoftwareSerial.h>

const uint8_t PIN_BT_RX = 10;
const uint8_t PIN_BT_TX = 11;
const uint8_t PIN_LED = 13;

SoftwareSerial bt(PIN_BT_RX, PIN_BT_TX);

unsigned long lastHello = 0;
bool ledState = false;

void setup() {
  pinMode(PIN_LED, OUTPUT);
  Serial.begin(9600);
  bt.begin(9600);
  Serial.println(F("Prueba HC-05 lista"));
}

void loop() {
  if (millis() - lastHello >= 1000) {
    lastHello = millis();
    bt.println(F("HOLA"));
    Serial.println(F("TX: HOLA"));
  }

  while (bt.available()) {
    char c = (char)bt.read();
    ledState = !ledState;
    digitalWrite(PIN_LED, ledState);
    Serial.print(F("RX: "));
    Serial.println((int)c);
    bt.print(F("ECO:"));
    bt.println(c);
  }
}
