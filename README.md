# Java senior platform

Aplicación de tesorería con Java 25, Spring Boot 4.1.1, PostgreSQL, RabbitMQ, Keycloak y Angular 22. Incluye dos servicios independientes: operaciones de cuentas y consulta de auditoría.

## Ejecutar en Windows

Necesitas Docker Desktop con contenedores Linux, PowerShell 7 y unos 6 GB disponibles para este entorno.

```powershell
./scripts/bootstrap.ps1
docker compose up --build -d
```

En Linux/macOS usa `sh scripts/bootstrap.sh` antes de Docker Compose. El script necesita OpenSSL. Abre http://localhost:8181 e inicia sesión con usuario `demo`. Su contraseña se genera en la variable `DEMO_PASSWORD` de `.env`. No se necesitan cuentas externas ni credenciales personales.

Keycloak se encuentra en http://localhost:8180. El administrador local es `local-admin`; su contraseña está en `ADMIN_PASSWORD`. El bootstrap conserva la configuración existente al repetirse.

Las bases y RabbitMQ solo se exponen a la red interna de Docker. Los puertos publicados están ligados a 127.0.0.1. Este Compose usa HTTP y el modo de desarrollo de Keycloak exclusivamente para pruebas locales. Un despliegue público requiere TLS, secretos gestionados y configuración de producción del proveedor de identidad.

## Uso

1. Inicia sesión y crea una cuenta USD o EUR. Empieza con saldo cero.
2. Selecciona la cuenta y registra un importe positivo para un depósito, negativo para un retiro.
3. Consulta los movimientos y la pestaña de auditoría. La auditoría puede tardar unos segundos.
4. Un retiro sin fondos se rechaza. Las operaciones ya registradas son inmutables; una corrección requiere un movimiento compensatorio.

Los endpoints se publican bajo `/api/accounts` y `/api/audit`. Todos requieren JWT con audiencia `ledger-api` y rol `operator`. El backend comprueba propiedad de cada cuenta. No se confía en un identificador de usuario recibido en el cuerpo.

La interfaz usa Authorization Code con PKCE S256. Conserva tokens en memoria; no almacena tokens en localStorage. Los dos clientes confidenciales `qa-alice` y `qa-bob` son identidades locales independientes utilizadas por las pruebas de API.

## Pruebas

```powershell
docker run --rm -v "${PWD}:/workspace" -v portfolio-maven:/root/.m2 -w /workspace maven:3.9.11-eclipse-temurin-25 mvn -B verify
python scripts/verify.py
```

La compilación Docker también ejecuta las pruebas Java. Las pruebas de integración requieren el entorno iniciado y Python 3.10 o posterior. Crean datos con identificadores únicos y prueban autorización, validación, decimales, reintentos idempotentes, concurrencia y entrega real de eventos.

Pruebas de formato monetario del frontend:

```powershell
docker run --rm -v "${PWD}/frontend:/app" -w /app node:24.15.0-alpine npm test
```

## Operación

```powershell
docker compose ps
docker compose logs --tail 80 ledger audit
docker compose down
```

`down` conserva los volúmenes. `docker compose down -v` elimina los datos de este proyecto; úsalo únicamente para reiniciar el entorno de prueba. Después de eliminar los volúmenes puede reutilizarse la misma configuración local.

Las migraciones Flyway se ejecutan al iniciar cada servicio. No hay consultas entre bases. El movimiento, el saldo y el outbox se confirman en una sola transacción. Los publicadores usan SKIP LOCKED, confirmaciones y comprobación de mensajes no enrutados. El consumidor evita repetir efectos mediante una clave única de evento. La concurrencia de cuentas se valida con una versión en PostgreSQL.

Los importes admiten dos decimales. `Idempotency-Key` es un UUID obligatorio en movimientos; reintentos idénticos devuelven el movimiento original, mientras un cuerpo diferente produce 409. Las consultas limitan resultados para evitar respuestas sin límite.

Los saldos y movimientos de las respuestas REST son cadenas decimales. La interfaz calcula y presenta centavos con BigInt, conservando precisión incluso por encima del rango seguro de enteros de JavaScript. Los eventos internos conservan decimales JSON que ambos servicios procesan como BigDecimal.

Para ejecutar las pruebas de recuperación de RabbitMQ y entrega duplicada:

```powershell
python scripts/verify.py --resilience
```

El comando detiene brevemente el broker de este Compose y lo vuelve a iniciar. Ejecútalo solo en el entorno local de pruebas. No ejecutes las pruebas de navegador simultáneamente con este ejercicio.

Para las pruebas completas de interfaz necesitas Node 24:

```powershell
cd frontend
npm ci
npm test
npm run build
npx playwright install chromium
npm run test:e2e
npm audit --audit-level=high
```

Playwright comprueba inicio de sesión, creación de cuenta, depósito, errores de validación, auditoría y cierre de sesión en escritorio y móvil. Incluye axe para reglas automatizables WCAG 2.1 AA; no sustituye una evaluación manual completa de accesibilidad. Las credenciales se leen del `.env` generado y las trazas están desactivadas para no capturarlas.

El contrato de la API está en `contracts/openapi.json`; el contrato del evento está en `contracts/ledger-event.schema.json`. Las decisiones, límites operativos y controles de seguridad se documentan en `docs/`.

La matriz de seguridad y decisiones de arquitectura documentan controles verificados y límites. Este repositorio no constituye una certificación OWASP ni un sistema bancario listo para producción.
