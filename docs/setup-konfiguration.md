# Server-Konfiguration und Setup-Entwurf

`HolonetDiscordConfig` enthält die dauerhaft gültige Verbindung eines Discord-Servers.
Der Primärschlüssel ist die `guildId`; die Konfiguration gehört keinem einzelnen Mitglied.
Provider-Tokens werden wie bisher verschlüsselt gespeichert.

Bei jedem `/setup` gilt diese Reihenfolge:

1. Eine vorhandene Server-Konfiguration befüllt das Formular und ersetzt dabei auch einen vorhandenen persönlichen Entwurf.
2. Ohne Konfiguration wird der noch gültige Entwurf für `guildId + memberId` fortgesetzt.
3. Ohne gültigen Entwurf startet ein neues, leeres Formular.

Das Modal zeigt beim Ticket-Provider „Gespeicherte Server-Konfiguration geladen.“ an,
wenn die Server-Konfiguration geladen wurde. Der gelbe Sicherheitshinweis stammt von
Discord und lässt sich über die Modal-API nicht ersetzen. Das Formular nutzt bereits
alle fünf möglichen Komponenten; der eigene Hinweis verwendet deshalb die Beschreibung
des ersten Feldes und benötigt kein zusätzliches Eingabefeld.

Öffnen und Absenden erneuern die Gültigkeit des Entwurfs um eine Stunde. Tippen in einem
geöffneten Modal sendet keine Aktivität an den Bot. Nach Ablauf wird ein Entwurf beim
nächsten Zugriff geschlossen. Beim Absenden eines älteren Modals werden dessen Werte
in einen gültigen Entwurf übernommen und geprüft. Abgelaufene Datensätze werden derzeit
nicht automatisch aus der Datenbank gelöscht.

Erst nach erfolgreichem Verbindungstest wird der Entwurf zur Server-Konfiguration und
die Session geschlossen. Ein fehlgeschlagener Test verändert die gültige Konfiguration
nicht. Wird danach `/setup` erneut ausgeführt, gewinnt wieder die gespeicherte
Konfiguration; fehlgeschlagene Änderungen müssen dann erneut eingegeben werden.
Ohne gespeicherte Konfiguration bleiben sie im persönlichen Entwurf erhalten.

Mehrere Mitglieder können gleichzeitig bearbeiten. Die zuletzt erfolgreich gespeicherte
Konfiguration gewinnt; ein Schutz gegen das Überschreiben zwischenzeitlicher Änderungen
ist in diesem Vorschlag nicht enthalten.

Die Migration legt die Konfigurationstabelle an. Bestehende Setup-Formulare bleiben
Entwürfe und werden nicht automatisch zu gültigen Konfigurationen, da die bisherigen
Daten keinen erfolgreichen Verbindungstest nachweisen. Nach dem Update muss die
gewünschte Verbindung einmal erfolgreich über `/setup` gespeichert werden.

Die Ticket-Übermittlung liest die dauerhafte Konfiguration statt persönlicher Entwürfe.
Der bestehende Stand zur tatsächlichen Auslösung einer Ticket-Übermittlung bleibt bestehen.
