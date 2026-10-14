# Servicio de cuentas

Microservicio independiente para crear y consultar cuentas SAVINGS o CHECKING asociadas a usuarios.
Forma el segundo productor del patrón fan-in junto a la API de usuarios existente.

## Responsabilidades
- POST /api/accounts: crea una cuenta y un evento account.created en una sola transacción.
- GET /api/accounts/{id}: consulta una cuenta.
- GET /api/accounts?userId=...: consulta las primeras 100 cuentas de un usuario.
- GET /health: comprueba que el proceso HTTP responde; /actuator/health comprueba también la base.

Usuarios mantiene autenticación, perfiles, avatares y Valkey.
Este servicio no crea usuarios, no guarda contraseñas y no implementa saldos ni transferencias.
Consulta al usuario vía HTTPS usando el JWT ADMIN recibido, sin leer su tabla.
El correo y el nombre del evento proceden de esa consulta, nunca del cuerpo enviado al crear la cuenta.

## Repositorios relacionados
- [Usuarios](https://github.com/duvanAL/users-management-spring-boot-hexagonal-ddd).
- [Notificaciones](https://github.com/duvanAL/users-notification-service).
- [Contrato Kafka](contracts/README.md).

## Configuración
Copiar .env.example a .env para Docker, o configurar las variables en el proveedor.
.env no se carga automáticamente al usar java -jar: exportar variables o usar docker --env-file .env.

JWT_SECRET: mismo secreto Base64 de la API de usuarios. No crear otro: invalidaría sus JWT.
USERS_API_URL: URL HTTPS de la API de usuarios, sin /api/users.
ACCOUNTS_DATABASE_URL: URL JDBC PostgreSQL, por ejemplo jdbc:postgresql://HOST:5432/DB?sslmode=require.
ACCOUNTS_DATABASE_USERNAME y ACCOUNTS_DATABASE_PASSWORD: credenciales del servicio de cuentas.

La persistencia real del proyecto es PostgreSQL, no MySQL.
El esquema propio accounts evita leer o escribir las tablas de usuarios.
Puede vivir en otra base PostgreSQL o, para el laboratorio, en el mismo servidor con un rol limitado a accounts.
Aplicar src/main/resources/schema.sql usando una conexión autorizada antes del despliegue.
Dejar DB_SCHEMA_INIT_MODE=never en producción.

Kafka: KAFKA_BOOTSTRAP_SERVERS, KAFKA_USERNAME, KAFKA_PASSWORD y
KAFKA_SSL_TRUST_CERTIFICATES (PEM de la CA Aiven; admite saltos reales o \\n).
SASL_SSL y SCRAM-SHA-256 son los valores predeterminados.
La identidad accounts-service solo necesita escribir en user.notification.requested.
No reutilizar una identidad administrativa para la aplicación.

POST exige ADMIN. GET exige ADMIN o REVIEWER.
Los JWT se validan localmente; este laboratorio comparte la clave HMAC con usuarios.
Una evolución de producción debería usar un emisor central y claves asimétricas.

## Ejecutar y verificar
Java 17 o superior:
```powershell
.\mvnw.cmd -B clean verify
docker build -t users-accounts-service .
docker run --rm --env-file .env -p 8081:8081 users-accounts-service
```
Linux: ./mvnw -B clean verify.
Los tests usan H2 y un directorio de usuarios simulado. No llaman servicios reales.

Importar postman/FanIn.postman_collection.json; sus pasos permiten crear usuario y cuenta.
La prueba completa reproducible con PostgreSQL y Kafka reales está en el repositorio de notificaciones:
scripts/smoke_fanin.py. Brevo es simulado para no enviar correo real.

## Despliegue posterior en Azure
Ejecutar manualmente Actions -> Publish container para publicar la imagen en GHCR.
La imagen queda en ghcr.io/duvanal/users-accounts-service; configurar visibilidad o acceso del registro.
Elegir el tag del commit para un despliegue reproducible.
La aplicación escucha PORT (8081 por defecto). Para consumo continuo establecer mínimo 1 réplica.
La suscripción Azure for Students y el crédito se configurarán aparte; este repositorio no crea recursos Azure.

## Origen
Servicio creado para el laboratorio Electiva II de UNICOLOMBO.
Arquitectura de referencia: guía parte 6 de John Carlos Arrieta y su notification-service.
Adaptado a Kafka/Aiven y Brevo; no requiere RabbitMQ ni código del otro repositorio para compilar.
