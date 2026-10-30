# Security Improvement Program — FinControl

Documento requerido por la Actividad 2.5: programa estructurado para **evaluar y mejorar** la seguridad de la aplicación de forma continua, con proceso de revisión periódica, métricas y plan de acción futuro.

## 1. Objetivo

Mantener y mejorar la postura de seguridad de FinControl a lo largo del tiempo, en lugar de tratar la seguridad como un hito único. El programa se alinea con **OWASP MASVS** (Mobile Application Security Verification Standard) y el programa **App Security Improvement (ASI) de Google**.

## 2. Alcance

- Código y configuración de la app (módulos auth, gastos, suscripciones, mapa).
- Datos locales (SQLite, preferencias cifradas).
- Comunicación de red (Google Maps / Play Services).
- Gestión de secretos (clave de Maps, credenciales de usuarios).

## 3. Ciclo de mejora (proceso de revisión periódica)

| Paso | Actividad | Frecuencia | Herramienta |
|------|-----------|------------|-------------|
| 1 | Análisis estático del APK y del código | Cada release + cada cambio en auth/datos | MobSF (`mobsfscan` / consola), Android Lint |
| 2 | Análisis dinámico en emulador/dispositivo | Cada release mayor | MobSF Dynamic, `adb` |
| 3 | Revisión de dependencias | Mensual | Gradle versions, advisories de Google/AOSP |
| 4 | Revisión de secretos y claves | Cada release | `git grep`, restricciones de API en Google Cloud |
| 5 | Pruebas de aceptación de seguridad | Antes de cada entrega | Checklist MASVS de este documento |
| 6 | Cierre: informe de hallazgos + correcciones | Cada ciclo | `vulnerabilities.md` (histórico) |

**Responsables:** el equipo de desarrollo (revisión cruzada de los commits que tocan `auth`, `db`, `session`, permisos o el manifiesto).

**Criterio de entrada/salida:** un ciclo inicia con cada incremento funcional y cierra cuando el análisis estático no reporta hallazgos nuevos de severidad alta/media y las pruebas de aceptación pasan.

## 4. Métricas clave

| Métrica | Definición | Meta | Valor actual |
|---------|------------|------|--------------|
| Hallazgos de severidad alta abiertos | Inyección, credenciales en claro, datos sin cifrar | 0 | 0 (v2) |
| Hallazgos de severidad media abiertos | Configuración débil, claves expuestas | 0 | 0 (v2) |
| % de consultas SQL parametrizadas | Consultas con `selectionArgs` / total | 100% | 100% |
| Iteraciones de derivación de contraseñas | PBKDF2 (OWASP 2023 recomienda ≥ 600k SHA-512 / 210k SHA-256) | ≥ 150k | 150k |
| Cobertura de la checklist MASVS-L (local) | Ítems verificados / aplicables | 100% | Núcleo cubierto |
| Tiempo medio de corrección de un hallazgo | Días entre detección y commit de corrección | < 7 días | < 1 día (V1–V6) |
| Dependencias sin actualizar (seguridad) | Librerías AndroidX/GMS con versión obsoleta conocida | 0 | 0 |

## 5. Plan de acción futuro

| # | Acción | Prioridad | Horizonte |
|---|--------|-----------|-----------|
| A1 | Subir PBKDF2 a 210.000+ iteraciones o migrar a Argon2id (lib nativa) | Media | Próxima release |
| A2 | Play Integrity API / SafetyNet antes de operaciones sensibles | Alta | v3 (si la app se publica) |
| A3 | Rota cero-datos: opción de exportar/borrar todos los datos del usuario (derecho al olvido) | Media | v3 |
| A4 | Cifrar la base de datos SQLite completa (SQLCipher) además de los campos sensibles | Alta | v3 |
| A5 | Restringir la clave de Maps por huella SHA-1 + paquete en Google Cloud | Alta | Cumplido (clave restringida, `d666190`) |
| A6 | Pruebas automatizadas de seguridad en CI (mobsfscan + unit tests de UserDao) | Alta | Próximo sprint |
| A7 | Verificación de sesión en cambio de cuenta del dispositivo (biometría con BiometricPrompt) | Baja | v4 |
| A8 | Rate-limiting local de intentos de login fallidos | Media | v3 |

## 6. Lecciones del ciclo actual (v1 → v2)

- Encontrar vulnerabilidades temprano (línea base v1) permitió medir la corrección con evidencia en el historial (`vulnerabilities.md`, commits `43e9135` → `8ff0560`).
- Las inyecciones SQL y las contraseñas en claro eran los hallazgos de mayor impacto; las configuraciones (backup, tráfico en claro) eran rápidas de corregir y muy visibles para los análisis automatizados.
- Las métricas acordadas quedan como línea base para el siguiente ciclo.
